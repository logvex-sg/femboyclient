package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * AutoJump (automation): jumps when a one-block step blocks the player's path, the same idea as
 * the vanilla auto-jump option but with a configurable height and a cooldown so it does not
 * machine-gun the jump key.
 *
 * <p>It only presses the jump key through the vanilla input, never teleports or modifies the
 * world, and only when the player is on the ground and actually moving.
 */
public final class AutoJumpAutomation extends AutomationModule {

    private final IntSetting maxHeight = add("MaxHeight", "Tallest step to jump up.", 1, 1, 2);
    private final BooleanSetting onlyOnGround = add("OnlyOnGround", "Only jump while on the ground.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between jumps.", 6, 1, 30);

    private int ticks;

    public AutoJumpAutomation() {
        super(Category.AUTOMATION, "AutoJump", "Jumps one-block steps while moving.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client) || client.world == null) {
            return;
        }
        if (onlyOnGround.value() && !client.player.isOnGround()) {
            return;
        }
        if (++ticks < cooldown.value()) {
            return;
        }
        if (!client.player.input.hasForwardMovement()) {
            return;
        }
        if (blockedAhead(client)) {
            client.options.jumpKey.setPressed(true);
            ticks = 0;
        }
    }

    private boolean blockedAhead(MinecraftClient client) {
        var look = client.player.getRotationVecClient();
        BlockPos feet = client.player.getBlockPos();
        int dx = Math.abs(look.x) > Math.abs(look.z) ? (int) Math.signum(look.x) : 0;
        int dz = dx == 0 ? (int) Math.signum(look.z) : 0;
        if (dx == 0 && dz == 0) {
            return false;
        }
        BlockPos ahead = feet.add(dx, 0, dz);
        if (!client.world.getBlockState(ahead).getCollisionShape(client.world, ahead).isEmpty()) {
            // Something blocks the feet level; jump if the space above is clear up to maxHeight.
            for (int y = 1; y <= maxHeight.value(); y++) {
                BlockPos above = ahead.up(y);
                if (client.world.getBlockState(above).getCollisionShape(client.world, above).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.options.jumpKey.setPressed(false);
        }
    }
}
