package com.logvex.crystal.remote;

import com.logvex.crystal.remote.agent.AgentConfig;
import com.logvex.crystal.remote.agent.RemoteActionHandler;
import com.logvex.crystal.remote.agent.RemoteAgent;
import com.logvex.crystal.remote.controller.CommandHistory;
import com.logvex.crystal.remote.controller.InstanceRecord;
import com.logvex.crystal.remote.controller.RemoteController;
import com.logvex.crystal.remote.core.LinkConfig;
import com.logvex.crystal.remote.protocol.Capability;
import com.logvex.crystal.remote.protocol.CommandResult;
import com.logvex.crystal.remote.protocol.CommandStatus;
import com.logvex.crystal.remote.protocol.InstanceSnapshot;
import com.logvex.crystal.remote.protocol.RemoteAction;
import com.logvex.crystal.remote.security.AuditLog;
import com.logvex.crystal.remote.security.CommandAllowlist;
import com.logvex.crystal.remote.security.PairingRecord;
import com.logvex.crystal.remote.security.Permission;
import com.logvex.crystal.remote.security.RemoteCrypto;
import com.logvex.crystal.remote.security.SessionManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A real loopback controller + agent pair for the remote-control tests.
 *
 * <p>This is not a mock: it binds a real {@link java.net.ServerSocket} on port 0, dials it with a
 * real socket, and runs the real handshake, HMAC authentication and framing. Only the
 * {@link RemoteActionHandler} is a stub, because that is the seam where Minecraft would live and
 * the tests deliberately have no game. Everything the security and reliability tests exercise —
 * replay rejection, timeouts, revocation, reconnection — is therefore the production code path.
 */
public final class RemoteTestHarness implements AutoCloseable {

    public final AuditLog audit = new AuditLog(200);
    public final RemoteController controller;
    public final List<RemoteAgent> agents = new CopyOnWriteArrayList<>();
    public final List<StubHandler> handlers = new CopyOnWriteArrayList<>();
    private final LinkConfig linkConfig;

    public RemoteTestHarness() {
        this(LinkConfig.test());
    }

    public RemoteTestHarness(LinkConfig linkConfig) {
        this.linkConfig = linkConfig;
        this.controller = new RemoteController("test-controller", audit, new SessionManager(30_000L),
                new CommandHistory(100), linkConfig);
    }

    /** Binds the controller to an ephemeral port. */
    public int start() throws java.io.IOException {
        return controller.start(0);
    }

    /** Pairs an instance and returns the record (whose pairing code the agent will use). */
    public PairingRecord pair(String instanceId, String name, Set<Permission> permissions) {
        return controller.pair(instanceId, name, permissions);
    }

    /**
     * Starts an agent that uses {@code code} as its pairing code, connects it to the controller,
     * and waits until the controller reports it online (or the deadline passes).
     */
    public RemoteAgent connectAgent(String instanceId, String name, String code,
                                    StubHandler handler, long timeoutMillis) {
        AgentConfig config = new AgentConfig();
        config.enabled = true;
        config.controllerHost = "127.0.0.1";
        config.controllerPort = controller.port();
        config.instanceId = instanceId;
        config.instanceName = name;
        config.pairingCode = code;
        RemoteAgent agent = new RemoteAgent(config, new AuditLog(50), linkConfig, handler,
                CommandAllowlist.safeDefaults(), Set.of(Permission.VIEW_STATUS,
                Permission.CONTROL_MODULES, Permission.READ_DIAGNOSTICS, Permission.APPLY_PROFILES,
                Permission.EDIT_KEYBINDS, Permission.DISCONNECT));
        handlers.add(handler);
        agents.add(agent);
        agent.start();
        awaitOnline(instanceId, timeoutMillis);
        return agent;
    }

    /** Pairs and connects in one step, using the controller-generated code. */
    public RemoteAgent pairAndConnect(String instanceId, String name, StubHandler handler,
                                      long timeoutMillis) {
        PairingRecord record = pair(instanceId, name, Set.of(Permission.VIEW_STATUS,
                Permission.CONTROL_MODULES, Permission.READ_DIAGNOSTICS, Permission.APPLY_PROFILES,
                Permission.EDIT_KEYBINDS, Permission.DISCONNECT));
        return connectAgent(instanceId, name, record.pairingCode(), handler, timeoutMillis);
    }

    /**
     * Waits until the instance is ONLINE and has sent its first snapshot, so a test that then
     * reads the snapshot does not race the STATUS message that follows AUTH_OK.
     */
    public boolean awaitOnline(String instanceId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            InstanceRecord record = controller.instance(instanceId);
            if (record != null
                    && record.status() == com.logvex.crystal.remote.controller.InstanceStatus.ONLINE
                    && record.snapshot() != null) {
                return true;
            }
            sleep(20);
        }
        return false;
    }

    /** Waits until a command history entry has a terminal outcome for every target. */
    public boolean awaitComplete(CommandHistory.Entry entry, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (entry.complete() && !entry.outcomes().isEmpty()) {
                return true;
            }
            sleep(20);
        }
        return false;
    }

    public CommandStatus statusFor(CommandHistory.Entry entry, String instanceId) {
        for (CommandHistory.Outcome outcome : entry.outcomes()) {
            if (outcome.instanceId().equals(instanceId)) {
                return outcome.status();
            }
        }
        return null;
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        for (RemoteAgent agent : agents) {
            agent.close();
        }
        controller.close();
        sleep(50);
    }

    /**
     * A recording {@link RemoteActionHandler}: it records every action it is asked to run and
     * returns a configurable result, so a test can assert what reached the agent without a game.
     */
    public static final class StubHandler implements RemoteActionHandler {
        public final List<RemoteAction> executed = new CopyOnWriteArrayList<>();
        public final Map<String, Integer> counts = new ConcurrentHashMap<>();
        public final AtomicInteger snapshots = new AtomicInteger();
        public volatile String name = "stub";
        public volatile String mcVersion = "1.21.11";
        public volatile CommandStatus forcedStatus = CommandStatus.OK;
        public volatile String forcedMessage = "ok";
        public volatile long delayMillis = 0;
        public volatile boolean supportsModules = true;
        public volatile boolean canRestart = false;
        public final AtomicInteger restarts = new AtomicInteger();

        @Override
        public CommandResult execute(String requestId, String instanceId, RemoteAction action,
                                     Map<String, String> parameters) {
            executed.add(action);
            counts.merge(action.wire(), 1, Integer::sum);
            if (delayMillis > 0) {
                sleep(delayMillis);
            }
            if (action == RemoteAction.RESTART_AGENT) {
                restarts.incrementAndGet();
            }
            if (forcedStatus == CommandStatus.OK) {
                return CommandResult.ok(requestId, instanceId, forcedMessage);
            }
            return new CommandResult(requestId, instanceId, forcedStatus, forcedMessage,
                    System.currentTimeMillis());
        }

        @Override
        public InstanceSnapshot snapshot(String instanceId) {
            snapshots.incrementAndGet();
            return new InstanceSnapshot(instanceId, name, mcVersion, "1.0.0-test", 1, "", "overworld",
                    null, List.of("KillAura", "AutoTotem"), 240, 2, "idle", capabilities(),
                    System.currentTimeMillis());
        }

        @Override
        public Capability capabilities() {
            java.util.EnumSet<Capability.Flag> flags = java.util.EnumSet.of(
                    Capability.Flag.DIAGNOSTICS, Capability.Flag.PROFILES,
                    Capability.Flag.KEYBINDS, Capability.Flag.SAVE_CONFIG);
            if (supportsModules) {
                flags.add(Capability.Flag.MODULES);
            }
            if (canRestart) {
                flags.add(Capability.Flag.RESTART);
            }
            return new Capability(flags);
        }

        @Override
        public boolean canRestart() {
            return canRestart;
        }

        @Override
        public boolean restart() {
            restarts.incrementAndGet();
            return true;
        }
    }

    /** A convenience: an instance id that will not collide between tests. */
    public static String id(String suffix) {
        return "inst-" + suffix + "-" + RemoteCrypto.newSecret().substring(0, 6);
    }
}
