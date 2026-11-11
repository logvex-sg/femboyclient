package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;

/**
 * AutoPotion (automation): drinks a potion for a chosen beneficial effect when the player does not
 * already have it (or it is about to expire), covering the normal potion-use workflow only.
 *
 * <p>It recognises any drinkable item whose {@code POTION_CONTENTS} grant the wanted effect, so
 * splash bottles held in hand, custom potions and modded brews all work. It never throws a splash
 * potion, never moves items except to bring the bottle into the hand, and only acts through the
 * ordinary use action, so the server sees a normal drink.
 */
public final class AutoPotionAutomation extends AutomationModule {

    /** Which beneficial effect to keep up. */
    public enum Effect {
        FIRE_RESISTANCE,
        WATER_BREATHING,
        REGENERATION,
        STRENGTH,
        SPEED,
        NIGHT_VISION
    }

    private final EnumSetting<Effect> effect =
            add("Effect", "Which potion effect to keep active.", Effect.FIRE_RESISTANCE);
    private final IntSetting reapplySeconds =
            add("ReapplySeconds", "Drink again when the effect has less than this long left.", 8, 0, 60);
    private final BooleanSetting pullFromBackpack =
            add("PullFromBackpack", "Move a bottle into the hand when the hotbar has none.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between drinks.", 40, 5, 200);

    private int ticks;

    public AutoPotionAutomation() {
        super(Category.AUTOMATION, "AutoPotion",
                "Drinks a potion for a chosen effect when it lapses.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canActUnscreened(client)) {
            return;
        }
        if (client.player.isUsingItem()) {
            return;
        }
        RegistryEntry<StatusEffect> wanted = effectFor(effect.value());
        if (hasEffect(client, wanted)) {
            return;
        }
        if (++ticks < cooldown.value()) {
            return;
        }
        ticks = 0;
        if (holding(client, stack -> isPotionFor(stack, wanted))) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            return;
        }
        int hotbar = bestHotbar(client, stack -> isPotionFor(stack, wanted), stack -> 1);
        if (hotbar >= 0) {
            com.logvex.crystal.player.InventoryUtil.selectSlot(client, hotbar);
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            return;
        }
        if (!pullFromBackpack.value()) {
            return;
        }
        int backpack = bestBackpack(client, stack -> isPotionFor(stack, wanted), stack -> 1);
        pullToHand(client, backpack, stack -> isPotionFor(stack, wanted));
    }

    private boolean hasEffect(MinecraftClient client, RegistryEntry<StatusEffect> wanted) {
        StatusEffectInstance instance = client.player.getStatusEffect(wanted);
        if (instance == null) {
            return false;
        }
        // The duration is in ticks; drink early when the remaining time dips below the threshold.
        return instance.getDuration() > reapplySeconds.value() * 20;
    }

    /** True for a drinkable item whose potion contents include {@code wanted}. */
    private static boolean isPotionFor(ItemStack stack, RegistryEntry<StatusEffect> wanted) {
        if (stack.isEmpty() || stack.getUseAction() != UseAction.DRINK) {
            return false;
        }
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (StatusEffectInstance instance : contents.getEffects()) {
            if (instance.getEffectType().equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    private static RegistryEntry<StatusEffect> effectFor(Effect effect) {
        return switch (effect) {
            case FIRE_RESISTANCE -> StatusEffects.FIRE_RESISTANCE;
            case WATER_BREATHING -> StatusEffects.WATER_BREATHING;
            case REGENERATION -> StatusEffects.REGENERATION;
            case STRENGTH -> StatusEffects.STRENGTH;
            case SPEED -> StatusEffects.SPEED;
            case NIGHT_VISION -> StatusEffects.NIGHT_VISION;
        };
    }

    @Override
    public String statusLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return "idle";
        }
        StatusEffectInstance instance = client.player.getStatusEffect(effectFor(effect.value()));
        if (instance == null) {
            return effect.value() + ": inactive";
        }
        return effect.value() + ": " + (instance.getDuration() / 20) + "s";
    }
}
