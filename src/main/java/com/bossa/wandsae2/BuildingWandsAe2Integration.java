package com.bossa.wandsae2;

import org.slf4j.Logger;

import com.bossa.wandsae2.network.SetAutoCraftModePacket;
import com.mojang.logging.LogUtils;

import appeng.api.features.GridLinkables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(BuildingWandsAe2Integration.MODID)
public class BuildingWandsAe2Integration {
    public static final String MODID = "wandsae2";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation[] WAND_IDS = {
            ResourceLocation.fromNamespaceAndPath("wands", "stone_wand"),
            ResourceLocation.fromNamespaceAndPath("wands", "copper_wand"),
            ResourceLocation.fromNamespaceAndPath("wands", "iron_wand"),
            ResourceLocation.fromNamespaceAndPath("wands", "gold_wand"),
            ResourceLocation.fromNamespaceAndPath("wands", "diamond_wand"),
            ResourceLocation.fromNamespaceAndPath("wands", "netherite_wand"),
            ResourceLocation.fromNamespaceAndPath("wands", "creative_wand")
    };

    public BuildingWandsAe2Integration(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloadHandlers);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(MODID)
                .playToServer(SetAutoCraftModePacket.TYPE, SetAutoCraftModePacket.STREAM_CODEC,
                        SetAutoCraftModePacket::handle);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (ResourceLocation wandId : WAND_IDS) {
                if (!BuiltInRegistries.ITEM.containsKey(wandId)) {
                    LOGGER.warn("Could not register AE2 link handler for missing wand item {}", wandId);
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(wandId);
                GridLinkables.register(item, WandGridLinkableHandler.INSTANCE);
            }
            LOGGER.info("Registered Building Wands as AE2 grid-linkable items");
        });
    }
}
