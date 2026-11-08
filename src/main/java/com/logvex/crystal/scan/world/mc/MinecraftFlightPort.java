package com.logvex.crystal.scan.world.mc;

import com.logvex.crystal.scan.world.FlightPort;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.math.Vec3d;

/**
 * The real {@link FlightPort}, backed by the client player's own flight ability.
 *
 * <p>This is the client's authorised flight capability: the {@code flying} flag the game itself
 * exposes to the local player. The scanner never assumes it exists - {@link #available()} reports
 * whether the client exposes an ability object at all, and {@link #permitted()} reports whether
 * the server has granted {@code allowFlying} here - so an environment without flight simply
 * degrades to walking.
 *
 * <p>The scanner does not send its own ability packets beyond what the game already does when
 * {@code flying} changes; it toggles the same local flag the player would. A permission the server
 * refuses will simply be reverted by the server on its next abilities update, which the scanner
 * observes and falls back from.
 */
public final class MinecraftFlightPort implements FlightPort {

    private final MinecraftClient client;

    public MinecraftFlightPort(MinecraftClient client) {
        this.client = client;
    }

    private ClientPlayerEntity player() {
        return client == null ? null : client.player;
    }

    private PlayerAbilities abilities() {
        ClientPlayerEntity player = player();
        return player == null ? null : player.getAbilities();
    }

    @Override
    public boolean available() {
        return abilities() != null;
    }

    @Override
    public boolean flying() {
        PlayerAbilities abilities = abilities();
        return abilities != null && abilities.flying;
    }

    @Override
    public boolean permitted() {
        PlayerAbilities abilities = abilities();
        return abilities != null && (abilities.allowFlying || abilities.creativeMode);
    }

    @Override
    public boolean setFlying(boolean flying) {
        PlayerAbilities abilities = abilities();
        ClientPlayerEntity player = player();
        if (abilities == null || player == null) {
            return false;
        }
        abilities.flying = flying;
        player.sendAbilitiesUpdate();
        return true;
    }

    @Override
    public void setSpeed(double blocksPerSecond) {
        PlayerAbilities abilities = abilities();
        if (abilities == null) {
            return;
        }
        // The game's fly speed is a multiplier; ~0.05 is the vanilla default. Scale gently so a
        // request for a fast fly-over does not overshoot into a wall.
        float speed = (float) Math.max(0.05, Math.min(0.4, blocksPerSecond / 200.0));
        abilities.setFlySpeed(speed);
    }

    @Override
    public boolean inFluid() {
        ClientPlayerEntity player = player();
        return player != null && (player.isTouchingWater() || player.isSubmergedInWater());
    }

    @Override
    public boolean onGround() {
        ClientPlayerEntity player = player();
        return player != null && player.isOnGround();
    }

    @Override
    public double horizontalSpeed() {
        ClientPlayerEntity player = player();
        if (player == null) {
            return 0;
        }
        Vec3d velocity = player.getVelocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
    }

    @Override
    public String source() {
        PlayerAbilities abilities = abilities();
        if (abilities == null) {
            return "none";
        }
        if (abilities.creativeMode) {
            return "creative";
        }
        if (abilities.allowFlying) {
            return "allowed";
        }
        return "player";
    }
}
