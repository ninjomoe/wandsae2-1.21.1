package com.bossa.wandsae2.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.bossa.wandsae2.Ae2WandBridge;
import com.bossa.wandsae2.WandItemMenuHost;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.nicguzzo.wands.items.WandItem;

@Mixin(WandItem.class)
abstract class WandItemMenuMixin implements IMenuItem {
    @Override
    public ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, BlockHitResult hitResult) {
        return new WandItemMenuHost((WandItem) (Object) this, player, locator);
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void wandsae2$blockUseWhileAutoPlacementRuns(UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player != null && Ae2WandBridge.isAutoPlacementRunning(player)) {
            player.displayClientMessage(Component.literal("AE2 autocrafting is still running"), true);
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
