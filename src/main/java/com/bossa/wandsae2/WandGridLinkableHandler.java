package com.bossa.wandsae2;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.nicguzzo.wands.items.WandItem;

final class WandGridLinkableHandler implements IGridLinkableHandler {
    static final WandGridLinkableHandler INSTANCE = new WandGridLinkableHandler();

    private WandGridLinkableHandler() {
    }

    @Override
    public boolean canLink(ItemStack stack) {
        return stack.getItem() instanceof WandItem;
    }

    @Override
    public void link(ItemStack stack, GlobalPos pos) {
        stack.set(AEComponents.WIRELESS_LINK_TARGET, pos);
    }

    @Override
    public void unlink(ItemStack stack) {
        stack.remove(AEComponents.WIRELESS_LINK_TARGET);
    }
}
