package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;

/**
 * AutoSprint (automation): keeps the player sprinting under configurable conditions, with a
 * hunger floor so it never drains the last of the food bar and a toggle between always-on and
 * only-while-moving.
 */
public final class AutoSprintAutomation extends AutomationModule {

    /** When to force sprint on. */
    public enum Mode {
        ALWAYS,
        WHILE_MOVING,
        WHILE_MOVING_FORWARD
    }

    private final EnumSetting<Mode> mode = add("Mode", "When to sprint.", Mode.WHILE_MOVING_FORWARD);
    private final BooleanSetting onlyOnGround = add("OnlyOnGround", "Only force sprint on the ground.", false);
    private final BooleanSetting inWater = add("InWater", "Also sprint-swim in water.", true);
    private final IntSetting hungerFloor =
            add("HungerFloor", "Do not sprint below this food level.", 7, 0, 20);

    public AutoSprintAutomation() {
        super(Category.AUTOMATION, "AutoSprint", "Sprints automatically with a hunger floor.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client)) {
            return;
        }
        var player = client.player;
        if (player.getHungerManager().getFoodLevel() <= hungerFloor.value()) {
            return;
        }
        if (onlyOnGround.value() && !player.isOnGround()) {
            return;
        }
        if (player.isTouchingWater() && !inWater.value()) {
            return;
        }
        boolean moving = player.input.hasForwardMovement() || player.getVelocity().horizontalLengthSquared() > 1e-4;
        boolean should = switch (mode.value()) {
            case ALWAYS -> true;
            case WHILE_MOVING -> moving;
            case WHILE_MOVING_FORWARD -> player.input.hasForwardMovement();
        };
        if (should && !player.isSneaking()) {
            player.setSprinting(true);
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.setSprinting(false);
        }
    }
}
