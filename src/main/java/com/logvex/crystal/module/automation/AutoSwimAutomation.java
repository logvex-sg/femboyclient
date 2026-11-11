package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * AutoSwim (automation): keeps the player swimming upward when submerged, so crossing an ocean
 * does not end in drowning. It nudges upward velocity and toggles the sprint-swim state; it never
 * teleports, and it releases the upward nudge the moment the head is above water.
 */
public final class AutoSwimAutomation extends AutomationModule {

    private final BooleanSetting surface =
            add("Surface", "Push upward when submerged so you do not drown.", true);
    private final BooleanSetting fastSwim =
            add("FastSwim", "Hold the sprint-swim state while moving in water.", true);
    private final IntSetting airFloor =
            add("AirFloor", "Surface urgently once air drops below this.", 120, 0, 300);
    private final BooleanSetting onlyWhileMoving =
            add("OnlyWhileMoving", "Only swim up while the player is moving.", false);

    public AutoSwimAutomation() {
        super(Category.AUTOMATION, "AutoSwim", "Swims upward when submerged to avoid drowning.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client)) {
            return;
        }
        if (!client.player.isTouchingWater()) {
            return;
        }
        boolean moving = client.player.input.hasForwardMovement()
                || client.player.getVelocity().horizontalLengthSquared() > 1e-4;
        if (onlyWhileMoving.value() && !moving) {
            return;
        }
        if (fastSwim.value() && moving) {
            client.player.setSprinting(true);
        }
        boolean lowAir = client.player.getAir() <= airFloor.value();
        if (surface.value() && (client.player.isSubmergedInWater() || lowAir)) {
            Vec3d velocity = client.player.getVelocity();
            if (velocity.y < 0.02) {
                client.player.setVelocity(velocity.x, 0.05, velocity.z);
            }
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.setSprinting(false);
        }
    }
}
