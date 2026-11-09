package com.logvex.crystal.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.remote.controller.CommandHistory;
import com.logvex.crystal.remote.controller.InstanceStatus;
import com.logvex.crystal.remote.controller.RemoteController;
import com.logvex.crystal.remote.protocol.CommandStatus;
import com.logvex.crystal.remote.protocol.RemoteAction;
import com.logvex.crystal.remote.security.AuditLog;
import com.logvex.crystal.remote.security.Permission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests over a real loopback socket: pairing, authentication, dispatch, per-instance
 * results, unsupported capabilities, timeouts, bulk commands and shutdown.
 */
final class RemoteControllerAgentTest {

    @Test
    void pairedAgentRegistersAndShowsOnline() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            var handler = new RemoteTestHarness.StubHandler();
            harness.pairAndConnect("inst-1", "Main", handler, 3000);

            var record = harness.controller.instance("inst-1");
            assertNotNull(record);
            assertEquals(InstanceStatus.ONLINE, record.status());
            assertTrue(record.paired());
            assertEquals("1.21.11", record.minecraftVersion());
            assertNotNull(record.snapshot(), "the agent should have sent a STATUS snapshot");
            assertEquals(List.of("KillAura", "AutoTotem"), record.enabledModules());
        }
    }

    @Test
    void agentWithoutPairingIsNotAuthenticated() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            // No pair() call: the controller has no secret for this id.
            var handler = new RemoteTestHarness.StubHandler();
            var agent = harness.connectAgent("inst-unpaired", "Unknown", "AAAA-BBBB-CCCC-DDDD-EEEE",
                    handler, 1000);

            RemoteTestHarness.sleep(300);
            var record = harness.controller.instance("inst-unpaired");
            assertNotNull(record, "the controller should have seen the HELLO");
            assertFalse(record.paired());
            assertFalse(agent.authenticated(), "an unpaired agent must not be authenticated");
            assertTrue(record.status() == InstanceStatus.AWAITING_PAIRING
                            || record.status() == InstanceStatus.OFFLINE
                            || record.status() == InstanceStatus.UNAUTHORIZED,
                    "unpaired instance should not be ONLINE, was " + record.status());
        }
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            harness.pair("inst-bad", "Bad", Set.of(Permission.VIEW_STATUS));
            var handler = new RemoteTestHarness.StubHandler();
            // A syntactically valid pairing code that is not the one the controller issued.
            harness.connectAgent("inst-bad", "Bad", "MFRGGZDFMZTWQ2LKNNWG23TP", handler, 800);

            RemoteTestHarness.sleep(400);
            var record = harness.controller.instance("inst-bad");
            assertNotNull(record);
            assertEquals(InstanceStatus.UNAUTHORIZED, record.status());
            assertTrue(harness.audit.recent(20).stream()
                            .anyMatch(e -> e.kind() == AuditLog.Kind.AUTH_FAILURE),
                    "an authentication failure must be audited");
        }
    }

    @Test
    void dispatchReachesAgentAndReturnsResult() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            var handler = new RemoteTestHarness.StubHandler();
            harness.pairAndConnect("inst-1", "Main", handler, 3000);

            RemoteController.Dispatch dispatch = harness.controller.dispatch(List.of("inst-1"),
                    RemoteAction.ENABLE_MODULE, Map.of("module", "KillAura"));
            assertTrue(harness.awaitComplete(dispatch.entry(), 3000), "command should complete");
            assertEquals(CommandStatus.OK, harness.statusFor(dispatch.entry(), "inst-1"));
            assertEquals(1, handler.counts.getOrDefault("enable_module", 0));
        }
    }

    @Test
    void unsupportedCapabilityIsReportedNotFaked() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            var handler = new RemoteTestHarness.StubHandler();
            handler.canRestart = false; // so RESTART is not advertised
            harness.pairAndConnect("inst-1", "Main", handler, 3000);

            RemoteController.Dispatch dispatch = harness.controller.dispatch(List.of("inst-1"),
                    RemoteAction.RESTART_AGENT, Map.of());
            assertTrue(harness.awaitComplete(dispatch.entry(), 2000));
            assertEquals(CommandStatus.UNSUPPORTED, harness.statusFor(dispatch.entry(), "inst-1"));
            assertEquals(0, handler.restarts.get(), "an unsupported action must not run");
        }
    }

    @Test
    void bulkCommandGivesPerInstanceResults() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            var a = new RemoteTestHarness.StubHandler();
            var b = new RemoteTestHarness.StubHandler();
            var c = new RemoteTestHarness.StubHandler();
            c.supportsModules = false; // so ENABLE_MODULE is unsupported on c
            harness.pairAndConnect("inst-a", "A", a, 3000);
            harness.pairAndConnect("inst-b", "B", b, 3000);
            harness.pairAndConnect("inst-c", "C", c, 3000);

            RemoteController.Dispatch dispatch = harness.controller.dispatch(
                    List.of("inst-a", "inst-b", "inst-c"), RemoteAction.DISABLE_MODULE,
                    Map.of("module", "KillAura"));
            assertTrue(harness.awaitComplete(dispatch.entry(), 4000));

            assertEquals(CommandStatus.OK, harness.statusFor(dispatch.entry(), "inst-a"));
            assertEquals(CommandStatus.OK, harness.statusFor(dispatch.entry(), "inst-b"));
            assertEquals(CommandStatus.UNSUPPORTED, harness.statusFor(dispatch.entry(), "inst-c"));
            assertEquals(2, dispatch.entry().okCount());
            assertEquals(1, dispatch.entry().unsupportedCount());
            assertEquals(1, a.counts.getOrDefault("disable_module", 0));
            assertEquals(1, b.counts.getOrDefault("disable_module", 0));
            assertEquals(0, c.counts.getOrDefault("disable_module", 0));
        }
    }

    @Test
    void offlineInstanceIsRejectedNotSilentlySkipped() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            // Dispatch to an id that never connected.
            RemoteController.Dispatch dispatch = harness.controller.dispatch(List.of("ghost"),
                    RemoteAction.ENABLE_MODULE, Map.of("module", "KillAura"));
            assertTrue(harness.awaitComplete(dispatch.entry(), 1000));
            assertEquals(CommandStatus.REJECTED, harness.statusFor(dispatch.entry(), "ghost"));
        }
    }

    @Test
    void timeoutIsReportedWhenAgentDoesNotAnswer() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            harness.controller.setCommandTimeoutMillis(400);
            harness.controller.setMaxRetries(0);
            var handler = new RemoteTestHarness.StubHandler();
            handler.delayMillis = 1500; // answers long after the timeout
            harness.pairAndConnect("inst-slow", "Slow", handler, 3000);

            RemoteController.Dispatch dispatch = harness.controller.dispatch(List.of("inst-slow"),
                    RemoteAction.REQUEST_DIAGNOSTICS, Map.of());
            // Wait for the timeout outcome specifically.
            long deadline = System.currentTimeMillis() + 3000;
            CommandStatus status = null;
            while (System.currentTimeMillis() < deadline) {
                status = harness.statusFor(dispatch.entry(), "inst-slow");
                if (status != null && status.terminal() && status != CommandStatus.OK) {
                    break;
                }
                RemoteTestHarness.sleep(20);
            }
            assertEquals(CommandStatus.TIMEOUT, status,
                    "no acknowledgement before the deadline must be a timeout, not success");
        }
    }

    @Test
    void revokingATokenStopsAuthentication() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            var handler = new RemoteTestHarness.StubHandler();
            harness.pairAndConnect("inst-1", "Main", handler, 3000);
            assertEquals(InstanceStatus.ONLINE, harness.controller.instance("inst-1").status());

            harness.controller.revoke("inst-1");
            RemoteTestHarness.sleep(400);
            var record = harness.controller.instance("inst-1");
            assertEquals(InstanceStatus.UNAUTHORIZED, record.status());
            assertFalse(record.paired());
            assertTrue(harness.audit.recent(20).stream()
                    .anyMatch(e -> e.kind() == AuditLog.Kind.TOKEN_REVOKED));
        }
    }

    @Test
    void controllerShutdownIsCleanAndAgentSurvives() throws Exception {
        RemoteTestHarness harness = new RemoteTestHarness();
        harness.start();
        var handler = new RemoteTestHarness.StubHandler();
        var agent = harness.pairAndConnect("inst-1", "Main", handler, 3000);
        assertTrue(agent.authenticated());

        harness.controller.close();
        RemoteTestHarness.sleep(400);
        // The agent's own process keeps running; it simply loses its link and will retry.
        assertFalse(harness.controller.running());
        harness.close();
    }

    @Test
    void multipleAgentsRegisterSimultaneously() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            for (int i = 0; i < 8; i++) {
                var ag = harness.pairAndConnect("inst-" + i, "Instance " + i,
                        new RemoteTestHarness.StubHandler(), 4000);
                System.out.println("DIAG after " + i + " size=" + harness.controller.size()
                        + " online=" + harness.controller.onlineCount()
                        + " agentAuth=" + ag.authenticated() + " agentErr=" + ag.lastError()
                        + " attempts=" + ag.reconnectAttempts()
                        + " recErr=" + (harness.controller.instance("inst-" + i) == null ? "n/a"
                        : harness.controller.instance("inst-" + i).lastError()));
            }
            for (var r : harness.controller.instances()) {
                System.out.println("DIAG " + r.instanceId() + " status=" + r.status()
                        + " paired=" + r.paired() + " snap=" + (r.snapshot() != null));
            }
            System.out.println("DIAG size=" + harness.controller.size()
                    + " online=" + harness.controller.onlineCount());
            assertEquals(8, harness.controller.onlineCount());
            assertEquals(8, harness.controller.size());
        }
    }

    @Test
    void renamedInstancePersistsInRegistry() throws Exception {
        try (RemoteTestHarness harness = new RemoteTestHarness()) {
            harness.start();
            harness.pairAndConnect("inst-1", "Old", new RemoteTestHarness.StubHandler(), 3000);
            harness.controller.rename("inst-1", "New name");
            assertEquals("New name", harness.controller.instance("inst-1").name());
            harness.controller.addToGroup("inst-1", "PvP");
            assertTrue(harness.controller.instance("inst-1").inGroup("PvP"));
            assertTrue(harness.controller.groups().contains("PvP"));
        }
    }
}
