package com.bossa.wandsae2;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.PlayerSource;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.nicguzzo.wands.wand.BlockAccounting;
import net.nicguzzo.wands.wand.Wand;

public final class Ae2WandBridge {
    private static final Map<String, Long> LAST_CRAFT_REQUEST = new ConcurrentHashMap<>();
    private static final long CRAFT_REQUEST_COOLDOWN_TICKS = 40;

    private Ae2WandBridge() {
    }

    public static void addAvailableBlocks(Wand wand) {
        GridContext context = getGridContext(wand);
        if (context == null) {
            return;
        }

        for (Map.Entry<Item, BlockAccounting> entry : wand.block_accounting.entrySet()) {
            AEItemKey key = AEItemKey.of(entry.getKey());
            long amount = context.grid.getStorageService().getCachedInventory().get(key);
            if (amount > 0) {
                entry.getValue().in_player = saturatedAdd(entry.getValue().in_player, amount);
            }
        }
    }

    public static void extractPlacedBlocks(Wand wand) {
        GridContext context = getGridContext(wand);
        if (context == null) {
            return;
        }

        IActionSource source = new PlayerSource(context.player);
        for (Map.Entry<Item, BlockAccounting> entry : wand.block_accounting.entrySet()) {
            BlockAccounting accounting = entry.getValue();
            if (accounting.placed <= 0) {
                continue;
            }

            AEItemKey key = AEItemKey.of(entry.getKey());
            long extracted = StorageHelper.poweredExtraction(
                    context.grid.getEnergyService(),
                    context.grid.getStorageService().getInventory(),
                    key,
                    accounting.placed,
                    source,
                    Actionable.MODULATE);
            accounting.placed -= (int) Math.min(Integer.MAX_VALUE, extracted);
        }
    }

    public static void requestMissingCrafts(Wand wand) {
        GridContext context = getGridContext(wand);
        if (context == null) {
            return;
        }

        ICraftingService crafting = context.grid.getCraftingService();
        for (Map.Entry<Item, BlockAccounting> entry : wand.block_accounting.entrySet()) {
            BlockAccounting accounting = entry.getValue();
            int missing = accounting.needed - accounting.in_player;
            if (missing <= 0) {
                continue;
            }

            AEItemKey key = AEItemKey.of(entry.getKey());
            if (!crafting.isCraftable(key)) {
                continue;
            }
            if (!shouldRequestCraft(context.player, key)) {
                continue;
            }

            openCraftingAmountAfterWandAction(context.player, wand, key, getCraftAmount(wand, missing));
            return;
        }
    }

    private static int getCraftAmount(Wand wand, int missing) {
        ItemStack wandStack = wand.wand_stack;
        if (Ae2WandSettings.isCraftExcess(wandStack)) {
            return saturatedAdd(missing, Ae2WandSettings.getCraftExcessAmount(wandStack));
        }
        return missing;
    }

    public static boolean canCraftAllMissingBlocks(Wand wand) {
        GridContext context = getGridContext(wand);
        if (context == null) {
            return false;
        }

        boolean hasMissing = false;
        ICraftingService crafting = context.grid.getCraftingService();
        for (Map.Entry<Item, BlockAccounting> entry : wand.block_accounting.entrySet()) {
            BlockAccounting accounting = entry.getValue();
            if (accounting.needed > accounting.in_player) {
                hasMissing = true;
                if (!crafting.isCraftable(AEItemKey.of(entry.getKey()))) {
                    return false;
                }
            }
        }
        return hasMissing;
    }

    private static void openCraftingAmountAfterWandAction(ServerPlayer player, Wand wand, AEItemKey key, int missing) {
        player.getServer().execute(() -> {
            if (player.hasDisconnected()) {
                return;
            }

            ItemMenuHostLocator locator = getWandMenuLocator(player, wand);
            if (locator == null) {
                BuildingWandsAe2Integration.LOGGER.warn(
                        "Could not find wand stack in player inventory to open AE2 craft confirm menu for {} x {}",
                        missing, key);
                return;
            }

            WandItemMenuHost host = locator.locate(player, WandItemMenuHost.class);
            if (host == null || host.getActionableNode() == null) {
                BuildingWandsAe2Integration.LOGGER.warn(
                        "Could not resolve linked wand AE2 host to open AE2 craft amount menu for {} x {}",
                        missing, key);
                return;
            }

            if (Ae2WandSettings.isAutoCraftWithoutAsking(resolveWandStack(player, wand))) {
                submitCraftingJobSilently(player, host, key, missing);
            } else {
                player.displayClientMessage(Component.literal("Crafting missing blocks"), true);
                CraftAmountMenu.open(player, locator, key, missing);
            }
        });
    }

    private static void submitCraftingJobSilently(ServerPlayer player, WandItemMenuHost host, AEItemKey key, int missing) {
        IGridNode node = host.getActionableNode();
        if (node == null || node.getGrid() == null) {
            return;
        }

        ICraftingService crafting = node.getGrid().getCraftingService();
        IActionSource source = new PlayerSource(player, host);
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return source;
            }

            @Override
            public IGridNode getGridNode() {
                return node;
            }
        };

        Future<ICraftingPlan> future = crafting.beginCraftingCalculation(
                player.level(),
                requester,
                key,
                missing,
                CalculationStrategy.REPORT_MISSING_ITEMS);

        CompletableFuture.supplyAsync(() -> awaitCraftingPlan(future, key, missing))
                .thenAccept(plan -> player.getServer().execute(() -> submitCraftingPlan(player, crafting, source, plan)));
    }

    private static ICraftingPlan awaitCraftingPlan(Future<ICraftingPlan> future, AEKey key, int missing) {
        try {
            return future.get();
        } catch (Exception e) {
            BuildingWandsAe2Integration.LOGGER.warn("Could not plan silent AE2 craft for {} x {}", missing, key, e);
            return null;
        }
    }

    private static void submitCraftingPlan(ServerPlayer player, ICraftingService crafting, IActionSource source,
            ICraftingPlan plan) {
        if (player.hasDisconnected() || plan == null) {
            return;
        }

        if (plan.simulation() || !plan.missingItems().isEmpty()) {
            player.displayClientMessage(Component.literal("Auto-Crafting failed: Missing Ingredients"), true);
            return;
        }

        ICraftingSubmitResult result = crafting.submitJob(plan, null, null, true, source);
        if (result.successful()) {
            player.displayClientMessage(Component.literal("Crafting missing blocks"), true);
        } else {
            BuildingWandsAe2Integration.LOGGER.warn(
                    "Could not submit silent AE2 craft for {} x {}: {} ({})",
                    plan.finalOutput().amount(),
                    plan.finalOutput().what(),
                    result.errorCode(),
                    result.errorDetail());
        }
    }

    private static ItemMenuHostLocator getWandMenuLocator(ServerPlayer player, Wand wand) {
        ItemStack wandStack = resolveWandStack(player, wand);

        if (player.getMainHandItem() == wandStack) {
            return MenuLocators.forHand(player, InteractionHand.MAIN_HAND);
        }
        if (player.getOffhandItem() == wandStack) {
            return MenuLocators.forHand(player, InteractionHand.OFF_HAND);
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot) == wandStack) {
                return MenuLocators.forInventorySlot(slot);
            }
        }
        return null;
    }

    private static ItemStack resolveWandStack(ServerPlayer player, Wand wand) {
        ItemStack wandStack = wand.wand_stack;
        return wandStack == null || wandStack.isEmpty() ? player.getMainHandItem() : wandStack;
    }

    private static boolean shouldRequestCraft(ServerPlayer player, AEItemKey key) {
        String requestKey = player.getUUID() + ":" + key.getId();
        long now = player.level().getGameTime();
        Long last = LAST_CRAFT_REQUEST.get(requestKey);
        if (last != null && now - last < CRAFT_REQUEST_COOLDOWN_TICKS) {
            return false;
        }
        LAST_CRAFT_REQUEST.put(requestKey, now);
        return true;
    }

    private static GridContext getGridContext(Wand wand) {
        if (!(wand.player instanceof ServerPlayer player)) {
            return null;
        }

        ItemStack wandStack = wand.wand_stack;
        if (wandStack == null || wandStack.isEmpty()) {
            wandStack = player.getMainHandItem();
        }

        GlobalPos linkedPos = wandStack.get(AEComponents.WIRELESS_LINK_TARGET);
        if (linkedPos == null) {
            return null;
        }

        ServerLevel linkedLevel = player.getServer().getLevel(linkedPos.dimension());
        if (linkedLevel == null) {
            return null;
        }

        BlockEntity blockEntity = linkedLevel.getBlockEntity(linkedPos.pos());
        if (!(blockEntity instanceof IWirelessAccessPoint accessPoint)) {
            return null;
        }

        IGrid grid = accessPoint.getGrid();
        if (grid == null || !accessPoint.isActive()) {
            return null;
        }

        return new GridContext(player, grid, linkedPos);
    }

    private static int saturatedAdd(int current, long amount) {
        long result = current + amount;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private record GridContext(ServerPlayer player, IGrid grid, GlobalPos linkedPos) {
    }
}
