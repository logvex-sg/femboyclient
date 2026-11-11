package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.ItemScoring;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * AutoTool (automation): selects the hotbar tool the game itself considers best for the block
 * being looked at, using the vanilla tool component (correct-for-drops plus mining speed), so a
 * modded or custom tool is scored by its real numbers.
 *
 * <p>It only changes the selected hotbar slot, and only while the player is actually mining
 * (attack held) or when configured to always keep the right tool ready.
 */
public final class AutoToolAutomation extends AutomationModule {

    private final BooleanSetting onlyWhileMining =
            add("OnlyWhileMining", "Only switch while the attack key is held.", true);
    private final BooleanSetting keepOnSwitchBack =
            add("KeepTool", "Leave the tool selected after mining stops.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between switches.", 2, 1, 20);

    private int ticks;
    private int previousSlot = -1;

    public AutoToolAutomation() {
        super(Category.AUTOMATION, "AutoTool+", "Selects the best tool for the targeted block.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canActUnscreened(client)) {
            return;
        }
        if (++ticks < cooldown.value()) {
            return;
        }
        if (onlyWhileMining.value() && !client.options.attackKey.isPressed()) {
            restoreIfNeeded(client);
            return;
        }
        BlockPos pos = targetedBlock(client);
        if (pos == null) {
            restoreIfNeeded(client);
            return;
        }
        BlockState state = client.world.getBlockState(pos);
        int best = bestToolSlot(client, state);
        if (best < 0 || best == client.player.getInventory().getSelectedSlot()) {
            return;
        }
        if (previousSlot < 0) {
            previousSlot = client.player.getInventory().getSelectedSlot();
        }
        InventoryUtil.selectSlot(client, best);
        ticks = 0;
    }

    private void restoreIfNeeded(MinecraftClient client) {
        if (keepOnSwitchBack.value() || previousSlot < 0) {
            return;
        }
        InventoryUtil.selectSlot(client, previousSlot);
        previousSlot = -1;
    }

    private static BlockPos targetedBlock(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            return block.getBlockPos();
        }
        return null;
    }

    private static int bestToolSlot(MinecraftClient client, BlockState state) {
        int best = -1;
        double bestScore = 1.0; // Bare-hand speed; only switch when a tool genuinely beats it.
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            double score = ItemScoring.toolScore(stack, state);
            if (score > bestScore + 1e-6) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    @Override
    public String statusLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return "idle";
        }
        BlockPos pos = targetedBlock(client);
        if (pos == null) {
            return "no target";
        }
        return client.world.getBlockState(pos).getBlock().getName().getString();
    }

    @Override
    protected void onDisable() {
        previousSlot = -1;
    }
}
