package com.bossa.wandsae2.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.bossa.wandsae2.WandItemMenuHost;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.nicguzzo.wands.items.WandItem;

@Mixin(WandItem.class)
abstract class WandItemMenuMixin implements IMenuItem {
    @Override
    public ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, BlockHitResult hitResult) {
        return new WandItemMenuHost((WandItem) (Object) this, player, locator);
    }
}
