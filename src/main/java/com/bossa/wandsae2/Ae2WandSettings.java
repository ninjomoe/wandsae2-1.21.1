package com.bossa.wandsae2;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class Ae2WandSettings {
    private static final String AUTO_CRAFT_WITHOUT_ASKING = "wandsae2_auto_craft_without_asking";
    private static final String CRAFT_EXCESS = "wandsae2_craft_excess";
    private static final String CRAFT_EXCESS_AMOUNT = "wandsae2_craft_excess_amount";
    private static final String REPLACE_AIR_AND_WATER = "wandsae2_replace_air_and_water";
    public static final int MIN_CRAFT_EXCESS_AMOUNT = 1;
    public static final int MAX_CRAFT_EXCESS_AMOUNT = 1000;

    private Ae2WandSettings() {
    }

    public static boolean isAutoCraftWithoutAsking(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBoolean(AUTO_CRAFT_WITHOUT_ASKING);
    }

    public static void setAutoCraftWithoutAsking(ItemStack stack, boolean value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        updateTag(stack, tag -> tag.putBoolean(AUTO_CRAFT_WITHOUT_ASKING, value));
    }

    public static boolean isCraftExcess(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBoolean(CRAFT_EXCESS);
    }

    public static void setCraftExcess(ItemStack stack, boolean value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        updateTag(stack, tag -> tag.putBoolean(CRAFT_EXCESS, value));
    }

    public static int getCraftExcessAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return MIN_CRAFT_EXCESS_AMOUNT;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(CRAFT_EXCESS_AMOUNT)) {
            return MIN_CRAFT_EXCESS_AMOUNT;
        }
        return clampCraftExcessAmount(tag.getInt(CRAFT_EXCESS_AMOUNT));
    }

    public static void setCraftExcessAmount(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        updateTag(stack, tag -> tag.putInt(CRAFT_EXCESS_AMOUNT, clampCraftExcessAmount(amount)));
    }

    public static boolean isReplaceAirAndWater(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBoolean(REPLACE_AIR_AND_WATER);
    }

    public static void setReplaceAirAndWater(ItemStack stack, boolean value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        updateTag(stack, tag -> tag.putBoolean(REPLACE_AIR_AND_WATER, value));
    }

    public static int clampCraftExcessAmount(int amount) {
        return Math.clamp(amount, MIN_CRAFT_EXCESS_AMOUNT, MAX_CRAFT_EXCESS_AMOUNT);
    }

    private static void updateTag(ItemStack stack, java.util.function.Consumer<CompoundTag> updater) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        updater.accept(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
