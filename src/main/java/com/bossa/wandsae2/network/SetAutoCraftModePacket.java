package com.bossa.wandsae2.network;

import com.bossa.wandsae2.Ae2WandSettings;
import com.bossa.wandsae2.BuildingWandsAe2Integration;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.nicguzzo.wands.items.WandItem;

public record SetAutoCraftModePacket(boolean autoCraftWithoutAsking) implements CustomPacketPayload {
    public static final Type<SetAutoCraftModePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BuildingWandsAe2Integration.MODID, "set_auto_craft_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetAutoCraftModePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeBoolean(packet.autoCraftWithoutAsking()),
            buffer -> new SetAutoCraftModePacket(buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetAutoCraftModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack wand = findHeldWand(player);
                if (!wand.isEmpty()) {
                    Ae2WandSettings.setAutoCraftWithoutAsking(wand, packet.autoCraftWithoutAsking());
                }
            }
        });
    }

    private static ItemStack findHeldWand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WandItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
