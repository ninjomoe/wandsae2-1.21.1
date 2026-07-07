package com.bossa.wandsae2.mixin;

import java.util.concurrent.Future;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.bossa.wandsae2.WandItemMenuHost;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.helpers.PlayerSource;
import appeng.menu.me.crafting.CraftConfirmMenu;
import net.minecraft.world.level.Level;

@Mixin(CraftConfirmMenu.class)
abstract class CraftConfirmMenuMixin {
    @Redirect(
            method = "planJob",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation(Lnet/minecraft/world/level/Level;Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;"),
            remap = false)
    private Future<ICraftingPlan> wandsae2$planWithPlayerStorageAndWandGrid(
            ICraftingService craftingService,
            Level level,
            ICraftingSimulationRequester originalRequester,
            AEKey what,
            long amount,
            CalculationStrategy strategy) {
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        if (menu.getTarget() instanceof WandItemMenuHost host) {
            ICraftingSimulationRequester wandRequester = new ICraftingSimulationRequester() {
                @Override
                public IActionSource getActionSource() {
                    return new PlayerSource(menu.getPlayerInventory().player, host);
                }

                @Override
                public IGridNode getGridNode() {
                    return host.getActionableNode();
                }
            };
            return craftingService.beginCraftingCalculation(level, wandRequester, what, amount, strategy);
        }

        return craftingService.beginCraftingCalculation(level, originalRequester, what, amount, strategy);
    }
}
