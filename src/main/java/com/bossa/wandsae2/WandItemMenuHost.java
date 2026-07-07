package com.bossa.wandsae2;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.nicguzzo.wands.items.WandItem;

public final class WandItemMenuHost extends ItemMenuHost<WandItem> implements ISubMenuHost, IActionHost {
    private final IGridNode actionableNode;

    public WandItemMenuHost(WandItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);
        this.actionableNode = resolveActionableNode(player, getItemStack());
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu submenu) {
        player.closeContainer();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return getItemStack().copyWithCount(1);
    }

    @Override
    public IGridNode getActionableNode() {
        return actionableNode;
    }

    private static IGridNode resolveActionableNode(Player player, ItemStack stack) {
        if (player.getServer() == null) {
            return null;
        }

        GlobalPos linkedPos = stack.get(AEComponents.WIRELESS_LINK_TARGET);
        if (linkedPos == null) {
            return null;
        }

        ServerLevel level = player.getServer().getLevel(linkedPos.dimension());
        if (level == null) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(linkedPos.pos());
        if (blockEntity instanceof IWirelessAccessPoint accessPoint && accessPoint.isActive()) {
            return accessPoint.getActionableNode();
        }
        return null;
    }
}
