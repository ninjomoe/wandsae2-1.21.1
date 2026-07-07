package com.bossa.wandsae2.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.bossa.wandsae2.Ae2WandBridge;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.nicguzzo.wands.wand.InventoryManager;
import net.nicguzzo.wands.wand.Wand;

@Mixin(InventoryManager.class)
abstract class InventoryManagerMixin {
    @Inject(method = "check_inventory", at = @At("HEAD"), remap = false)
    private void wandsae2$addMeBlocksToAvailableCount(Wand wand, CallbackInfo ci) {
        Ae2WandBridge.addAvailableBlocks(wand);
    }

    @Inject(method = "check_inventory", at = @At("TAIL"), remap = false)
    private void wandsae2$requestMissingMeCrafts(Wand wand, CallbackInfo ci) {
        Ae2WandBridge.requestMissingCrafts(wand);
    }

    @Redirect(
            method = "check_inventory",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V",
                    remap = true),
            remap = false)
    private void wandsae2$suppressCraftableMissingBlocksMessage(Player player, Component message, boolean actionBar,
            Wand wand) {
        if (!Ae2WandBridge.canCraftAllMissingBlocks(wand)) {
            player.displayClientMessage(message, actionBar);
        }
    }

    @Inject(method = "remove_from_inventory", at = @At("TAIL"), remap = false)
    private void wandsae2$extractPlacedBlocksFromMe(Wand wand, int placed, CallbackInfo ci) {
        Ae2WandBridge.extractPlacedBlocks(wand);
    }
}
