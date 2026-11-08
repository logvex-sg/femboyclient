package com.logvex.crystal.scan.world.mc;

import com.logvex.crystal.integration.baritone.BaritoneGoal;
import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.scan.world.MovementPort;

/**
 * The real {@link MovementPort}, backed by the shared Baritone integration when it is present.
 *
 * <p>The scanner does not move the player itself: it asks Baritone to path to a waypoint and to
 * cancel. When Baritone is absent, {@link #available()} is false, {@link #goTo} is a no-op and the
 * scanner still works - it simply samples the chunks around wherever the player already is, which
 * is the honest behaviour for an environment with no navigation source. This port never issues a
 * command to a movement module the user did not enable; it only uses Baritone, which the user
 * controls through the client's own Baritone module.
 */
public final class BaritoneMovementPort implements MovementPort {

    private final BaritoneService baritone;

    public BaritoneMovementPort(BaritoneService baritone) {
        this.baritone = baritone;
    }

    @Override
    public boolean available() {
        return baritone != null && baritone.available();
    }

    @Override
    public void goTo(int x, int y, int z) {
        if (!available()) {
            return;
        }
        // A "near" goal tolerates terrain between here and the target; the caller supplies the
        // player's current y so Baritone does not path to a stale height.
        baritone.goTo(BaritoneGoal.near(x, y, z, 2));
    }

    @Override
    public void stop() {
        if (baritone != null && baritone.available()) {
            baritone.cancel();
        }
    }

    @Override
    public String source() {
        return available() ? "baritone" : "none";
    }
}
