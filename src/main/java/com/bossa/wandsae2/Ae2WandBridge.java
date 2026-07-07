package com.bossa.wandsae2;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.nicguzzo.wands.items.WandItem;
import net.nicguzzo.wands.wand.BlockAccounting;
import net.nicguzzo.wands.wand.Wand;

public final class Ae2WandBridge {
    private static final Map<String, Long> LAST_CRAFT_REQUEST = new ConcurrentHashMap<>();
    private static final List<PendingAutoPlacement> PENDING_AUTO_PLACEMENTS = new CopyOnWriteArrayList<>();
    private static final long CRAFT_REQUEST_COOLDOWN_TICKS = 40;
    private static final long AUTO_PLACE_TIMEOUT_TICKS = 20 * 60;
    private static final long AUTO_PLACE_POLL_INTERVAL_TICKS = 5;

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

            openCraftingAmountAfterWandAction(context.player, wand, key, missing, getCraftAmount(wand, missing));
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

    private static void openCraftingAmountAfterWandAction(ServerPlayer player, Wand wand, AEItemKey key,
            int requiredAmount, int craftAmount) {
        player.getServer().execute(() -> {
            if (player.hasDisconnected()) {
                return;
            }

            ItemMenuHostLocator locator = getWandMenuLocator(player, wand);
            if (locator == null) {
                BuildingWandsAe2Integration.LOGGER.warn(
                        "Could not find wand stack in player inventory to open AE2 craft confirm menu for {} x {}",
                        craftAmount, key);
                return;
            }

            WandItemMenuHost host = locator.locate(player, WandItemMenuHost.class);
            if (host == null || host.getActionableNode() == null) {
                BuildingWandsAe2Integration.LOGGER.warn(
                        "Could not resolve linked wand AE2 host to open AE2 craft amount menu for {} x {}",
                        craftAmount, key);
                return;
            }

            if (Ae2WandSettings.isAutoCraftWithoutAsking(resolveWandStack(player, wand))) {
                submitCraftingJobSilently(player, host, key, requiredAmount, craftAmount,
                        PendingAutoPlacement.create(player, wand, key, requiredAmount));
            } else {
                player.displayClientMessage(Component.literal("Crafting missing blocks"), true);
                CraftAmountMenu.open(player, locator, key, craftAmount);
            }
        });
    }

    private static void submitCraftingJobSilently(ServerPlayer player, WandItemMenuHost host, AEItemKey key,
            int requiredAmount, int craftAmount, PendingAutoPlacement pendingPlacement) {
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
                craftAmount,
                CalculationStrategy.REPORT_MISSING_ITEMS);

        CompletableFuture.supplyAsync(() -> awaitCraftingPlan(future, key, craftAmount))
                .thenAccept(plan -> player.getServer().execute(
                        () -> submitCraftingPlan(player, crafting, source, plan, pendingPlacement, requiredAmount)));
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
            ICraftingPlan plan, PendingAutoPlacement pendingPlacement, int requiredAmount) {
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
            if (pendingPlacement != null && requiredAmount > 0) {
                PENDING_AUTO_PLACEMENTS.add(pendingPlacement);
            }
        } else {
            BuildingWandsAe2Integration.LOGGER.warn(
                    "Could not submit silent AE2 craft for {} x {}: {} ({})",
                    plan.finalOutput().amount(),
                    plan.finalOutput().what(),
                    result.errorCode(),
                    result.errorDetail());
        }
    }

    public static void tickPendingAutoPlacements(MinecraftServer server) {
        if (PENDING_AUTO_PLACEMENTS.isEmpty()) {
            return;
        }

        long now = server.overworld().getGameTime();
        Iterator<PendingAutoPlacement> iterator = PENDING_AUTO_PLACEMENTS.iterator();
        while (iterator.hasNext()) {
            PendingAutoPlacement pending = iterator.next();
            if (pending.isExpired(now)) {
                PENDING_AUTO_PLACEMENTS.remove(pending);
                continue;
            }
            if (now < pending.nextCheckTick()) {
                continue;
            }
            pending.setNextCheckTick(now + AUTO_PLACE_POLL_INTERVAL_TICKS);

            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            if (player == null || player.hasDisconnected()) {
                PENDING_AUTO_PLACEMENTS.remove(pending);
                continue;
            }

            GridContext context = getGridContext(pending.wand());
            if (context == null) {
                PENDING_AUTO_PLACEMENTS.remove(pending);
                continue;
            }

            long available = context.grid().getStorageService().getCachedInventory().get(pending.key());
            if (available < pending.requiredAmount()) {
                continue;
            }

            PENDING_AUTO_PLACEMENTS.remove(pending);
            pending.run(player);
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

    private static final class PendingAutoPlacement {
        private final UUID playerId;
        private final Wand wand;
        private final AEItemKey key;
        private final int requiredAmount;
        private final long expireTick;
        private long nextCheckTick;
        private final BlockState blockState;
        private final net.minecraft.core.BlockPos pos;
        private final net.minecraft.core.Direction side;
        private final Vec3 hit;
        private final ItemStack wandStack;
        private final WandItem wandItem;

        private PendingAutoPlacement(ServerPlayer player, Wand wand, AEItemKey key, int requiredAmount) {
            this.playerId = player.getUUID();
            this.wand = wand;
            this.key = key;
            this.requiredAmount = requiredAmount;
            this.expireTick = player.level().getGameTime() + AUTO_PLACE_TIMEOUT_TICKS;
            this.nextCheckTick = player.level().getGameTime() + AUTO_PLACE_POLL_INTERVAL_TICKS;
            this.blockState = wand.block_state;
            this.pos = wand.pos == null ? null : wand.pos.immutable();
            this.side = wand.getSide();
            this.hit = wand.hit;
            this.wandStack = wand.wand_stack;
            this.wandItem = wand.wand_stack != null && wand.wand_stack.getItem() instanceof WandItem item ? item : null;
        }

        static PendingAutoPlacement create(ServerPlayer player, Wand wand, AEItemKey key, int requiredAmount) {
            return new PendingAutoPlacement(player, wand, key, requiredAmount);
        }

        boolean isExpired(long now) {
            return now > expireTick;
        }

        long nextCheckTick() {
            return nextCheckTick;
        }

        void setNextCheckTick(long nextCheckTick) {
            this.nextCheckTick = nextCheckTick;
        }

        UUID playerId() {
            return playerId;
        }

        Wand wand() {
            return wand;
        }

        AEItemKey key() {
            return key;
        }

        int requiredAmount() {
            return requiredAmount;
        }

        void run(ServerPlayer player) {
            if (wandItem == null || wandStack == null || wandStack.isEmpty() || pos == null || side == null) {
                return;
            }
            wand.do_or_preview(player, player.level(), blockState, pos, side, hit, wandStack, wandItem, false);
        }
    }
}
