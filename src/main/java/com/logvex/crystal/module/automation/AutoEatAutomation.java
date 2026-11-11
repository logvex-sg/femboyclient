package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * AutoEat (automation): eats the best food available when hunger drops, ranking food by the
 * game's own nutrition and saturation values so golden carrots, steak and modded food are all
 * compared fairly.
 *
 * <p>It will pull food from the backpack into the hand (as an arbitrated transaction) when the
 * hotbar has none, which the plain AutoEat will not do, and it respects the player's own use
 * state so it never eats while already eating or blocking.
 */
public final class AutoEatAutomation extends AutomationModule {

    /** Preference between food with the most nutrition and food with the best saturation. */
    public enum Preference {
        NUTRITION,
        SATURATION,
        BALANCED
    }

    private final IntSetting threshold = add("Threshold", "Eat when hunger is at or below this.", 16, 1, 20);
    private final EnumSetting<Preference> preference =
            add("Preference", "How to rank food.", Preference.BALANCED);
    private final BooleanSetting pullFromBackpack =
            add("PullFromBackpack", "Move food into the hand when the hotbar has none.", true);
    private final BooleanSetting eatInCombat = add("EatInCombat", "Also eat while a target is nearby.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between bites.", 10, 1, 60);

    private int ticks;

    public AutoEatAutomation() {
        super(Category.AUTOMATION, "AutoEat+",
                "Eats the best food by nutrition, pulling from the backpack when needed.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canActUnscreened(client)) {
            return;
        }
        if (client.player.getHungerManager().getFoodLevel() > threshold.value()) {
            return;
        }
        if (!eatInCombat.value() && context().combatService().currentTarget() != null) {
            return;
        }
        if (holding(client, AutoEatAutomation::isFood) && client.player.isUsingItem()) {
            return;
        }
        if (++ticks < cooldown.value()) {
            return;
        }
        ticks = 0;
        int hotbar = bestHotbar(client, AutoEatAutomation::isFood, this::score);
        if (hotbar >= 0) {
            InventoryUtil.selectSlot(client, hotbar);
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            return;
        }
        if (!pullFromBackpack.value()) {
            return;
        }
        int backpack = bestBackpack(client, AutoEatAutomation::isFood, this::score);
        pullToHand(client, backpack, AutoEatAutomation::isFood);
    }

    private static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
    }

    /** Score used to pick the best food for the configured preference. */
    private double score(ItemStack stack) {
        FoodComponent component = stack.get(DataComponentTypes.FOOD);
        if (component == null) {
            return 0;
        }
        return switch (preference.value()) {
            case NUTRITION -> component.nutrition();
            case SATURATION -> component.saturation();
            case BALANCED -> component.nutrition() + component.saturation();
        };
    }

    @Override
    public String statusLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return "idle";
        }
        int food = client.player.getHungerManager().getFoodLevel();
        ItemStack held = InventoryUtil.heldStack(client);
        String heldName = isFood(held) ? held.getName().getString() : "no food held";
        return "hunger " + food + " / " + heldName;
    }
}
