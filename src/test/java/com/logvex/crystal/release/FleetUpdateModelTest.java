package com.logvex.crystal.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Fleet-wide update decisions, the model the Overseer dashboard renders. */
class FleetUpdateModelTest {

    private static ReleaseManifest release(String minecraft, String version, int protocol) {
        return new ReleaseManifest(Edition.OVERSEER, version, minecraft, "modern", protocol,
                ">=0.20.1", "21", "logvex-crystal-overseer-" + minecraft + "-" + version + ".jar",
                "a".repeat(64), "sig", ReleaseChannel.STABLE);
    }

    private static FleetUpdateModel model(ReleaseManifest... releases) {
        return new FleetUpdateModel(Edition.OVERSEER, new ReleaseCatalog(List.of(releases)), 1, 1);
    }

    @Test
    void marksAMemberUpToDateWhenItRunsTheNewestRelease() {
        FleetUpdateModel model = model(release("1.21.11", "1.0.0", 1));
        var decision = model.decide(new FleetUpdateModel.Member("agent-1", "1.21.11", "1.0.0"));

        assertEquals(FleetUpdateModel.Status.UP_TO_DATE, decision.status());
        assertFalse(decision.actionable());
    }

    @Test
    void marksAMemberUpdatableWhenANewerReleaseExists() {
        FleetUpdateModel model = model(
                release("1.21.11", "1.0.0", 1),
                release("1.21.11", "1.1.0", 1));
        var decision = model.decide(new FleetUpdateModel.Member("agent-1", "1.21.11", "1.0.0"));

        assertEquals(FleetUpdateModel.Status.UPDATE_AVAILABLE, decision.status());
        assertTrue(decision.actionable());
        assertEquals("1.1.0", decision.target().clientVersion());
    }

    @Test
    void marksAMemberUnsupportedWhenNoReleaseMatchesItsMinecraftVersion() {
        FleetUpdateModel model = model(release("1.21.11", "1.0.0", 1));
        var decision = model.decide(new FleetUpdateModel.Member("agent-1", "1.20.1", "1.0.0"));

        assertEquals(FleetUpdateModel.Status.UNSUPPORTED, decision.status());
    }

    @Test
    void marksAMemberIncompatibleWhenOnlyItsProtocolDiffers() {
        FleetUpdateModel model = model(release("1.21.11", "1.1.0", 5));
        var decision = model.decide(new FleetUpdateModel.Member("agent-1", "1.21.11", "1.0.0"));

        assertEquals(FleetUpdateModel.Status.INCOMPATIBLE, decision.status());
    }

    @Test
    void decidesForEveryMemberAndSummarises() {
        FleetUpdateModel model = model(
                release("1.21.11", "1.1.0", 1),
                release("1.21.9", "1.1.0", 1));
        List<FleetUpdateModel.Member> members = List.of(
                new FleetUpdateModel.Member("a", "1.21.11", "1.0.0"),
                new FleetUpdateModel.Member("b", "1.21.11", "1.1.0"),
                new FleetUpdateModel.Member("c", "1.20.1", "1.0.0"));

        List<FleetUpdateModel.Decision> decisions = model.decide(members);

        assertEquals(3, decisions.size());
        assertEquals(FleetUpdateModel.Status.UPDATE_AVAILABLE, decisions.get(0).status());
        assertEquals(FleetUpdateModel.Status.UP_TO_DATE, decisions.get(1).status());
        assertEquals(FleetUpdateModel.Status.UNSUPPORTED, decisions.get(2).status());
        assertTrue(FleetUpdateModel.summarise(decisions).contains("3 instances"));
    }
}
