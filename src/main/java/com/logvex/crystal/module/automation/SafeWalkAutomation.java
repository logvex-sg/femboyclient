package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * SafeWalk (automation): presses sneak whenever the block the player is about to step onto has no
 * floor, so a walk along a bridge or a cliff edge will not fall off. It only ever sets the sneak
 * key; it never moves the player or places blocks.
 *
 * <p>The look-ahead is bounded: at most the block under the next step position, never a volume
 * scan, so it costs a couple of block lookups per tick.
 */
public final class SafeWalkAutomation extends AutomationModule {

    private final BooleanSetting onlyWhileMoving =
            add("OnlyWhileMoving", "Only sneak while the player is moving.", true);
    private final BooleanSetting edges =
            add("Edges", "Also protect when the block diagonally ahead is empty.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between sneak changes.", 1, 1, 10);

    private int ticks;

    public SafeWalkAutomation() {
        super(Category.AUTOMATION, "SafeWalk", "Sneaks at the edge of a drop so you cannot fall.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client) || client.world == null) {
            return;
        }
        if (++ticks < cooldown.value()) {
            return;
        }
        ticks = 0;
        Vec3d velocity = client.player.getVelocity();
        boolean moving = velocity.horizontalLengthSquared() > 1e-4
                || client.player.input.hasForwardMovement();
        if (onlyWhileMoving.value() && !moving) {
            return;
        }
        if (client.player.isTouchingWater() || client.player.isSwimming()) {
            return;
        }
        if (wouldFall(client, velocity)) {
            client.options.sneakKey.setPressed(true);
        }
    }

    /** True when the next step position has no floor under it. */
    private boolean wouldFall(MinecraftClient client, Vec3d velocity) {
        BlockPos feet = client.player.getBlockPos();
        int dx = (int) Math.signum(velocity.x);
        int dz = (int) Math.signum(velocity.z);
        if (dx == 0 && dz == 0) {
            // No velocity yet; use the facing direction so a stationary player at an edge is safe.
            var look = client.player.getRotationVecClient();
            dx = Math.abs(look.x) > Math.abs(look.z) ? (int) Math.signum(look.x) : 0;
            dz = dx == 0 ? (int) Math.signum(look.z) : 0;
        }
        if (dx == 0 && dz == 0) {
            return false;
        }
        return floorMissing(client, feet.add(dx, 0, dz))
                || (edges.value() && floorMissing(client, feet.add(dx, 0, 0))
                        && floorMissing(client, feet.add(0, 0, dz)));
    }

    private static boolean floorMissing(MinecraftClient client, BlockPos at) {
        var below = client.world.getBlockState(at.down());
        if (below.isAir() || below.isReplaceable()) {
            return true;
        }
        // A solid but non-full block (a slab, a fence) still supports a step; only a full drop
        // counts. A block with any collision shape counts as floor.
        return below.getCollisionShape(client.world, at.down()).isEmpty();
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.options.sneakKey.setPressed(false);
        }
    }
}
