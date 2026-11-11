package com.logvex.crystal.automation.inventory;

import com.logvex.crystal.player.InventoryUtil;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Read-only scoring of an item stack from its real data components, so AutoArmor, AutoWeapon and
 * AutoTool compare items by the numbers the game itself uses rather than a hand-written table.
 *
 * <p>Everything comes from {@code ATTRIBUTE_MODIFIERS} (armour points, attack damage) and the
 * vanilla {@code TOOL} component (mining speed, correct-for-drops), so a custom or modded item is
 * scored correctly. No method mutates anything.
 */
public final class ItemScoring {

    private ItemScoring() {
    }

    /** Armour points a piece contributes for {@code slot}, from its attribute modifiers. */
    public static double armorPoints(ItemStack stack, EquipmentSlot slot) {
        return attributeValue(stack, EntityAttributes.ARMOR, AttributeModifierSlot.ANY, armorSlot(slot));
    }

    /** Attack damage a held item contributes, from its attribute modifiers. */
    public static double attackDamage(ItemStack stack) {
        return attributeValue(stack, EntityAttributes.ATTACK_DAMAGE, AttributeModifierSlot.MAINHAND, null);
    }

    /** Mining speed an item has against a block, from the vanilla tool component. */
    public static float miningSpeed(ItemStack stack, BlockState state) {
        return stack.isEmpty() ? 1f : stack.getMiningSpeedMultiplier(state);
    }

    /** True when the item can actually harvest drops from the block (tier-correct). */
    public static boolean suitableFor(ItemStack stack, BlockState state) {
        return !stack.isEmpty() && stack.isSuitableFor(state);
    }

    /**
     * A single number combining correct-for-drops, mining speed and remaining durability, so a
     * better tool always outranks a worse one and a nearly-broken tool is penalised. Returns 0
     * for a stack that cannot mine the block at all.
     */
    public static double toolScore(ItemStack stack, BlockState state) {
        if (!suitableFor(stack, state)) {
            return 0;
        }
        float speed = miningSpeed(stack, state);
        float durability = InventoryUtil.durabilityFraction(stack);
        return speed * (0.5f + 0.5f * durability);
    }

    private static double attributeValue(ItemStack stack, RegistryEntry<EntityAttribute> attribute,
                                         AttributeModifierSlot any, AttributeModifierSlot exact) {
        if (stack.isEmpty()) {
            return 0;
        }
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0;
        }
        double total = 0;
        for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (!entry.attribute().equals(attribute)) {
                continue;
            }
            if (entry.modifier().operation() != EntityAttributeModifier.Operation.ADD_VALUE) {
                continue;
            }
            AttributeModifierSlot slot = entry.slot();
            if (slot == any || (exact != null && slot == exact)) {
                total += entry.modifier().value();
            }
        }
        return total;
    }

    private static AttributeModifierSlot armorSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> AttributeModifierSlot.HEAD;
            case CHEST -> AttributeModifierSlot.CHEST;
            case LEGS -> AttributeModifierSlot.LEGS;
            case FEET -> AttributeModifierSlot.FEET;
            default -> AttributeModifierSlot.ARMOR;
        };
    }
}
