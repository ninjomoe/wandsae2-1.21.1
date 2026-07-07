package com.bossa.wandsae2;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class Ae2WandSettings {
    private static final String AUTO_CRAFT_WITHOUT_ASKING = "wandsae2_auto_craft_without_asking";

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

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putBoolean(AUTO_CRAFT_WITHOUT_ASKING, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
