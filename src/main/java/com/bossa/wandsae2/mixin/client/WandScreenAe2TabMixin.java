package com.bossa.wandsae2.mixin.client;

import java.util.Vector;

import com.bossa.wandsae2.Ae2WandSettings;
import com.bossa.wandsae2.network.SetAutoCraftModePacket;

import appeng.api.ids.AEComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.nicguzzo.wands.client.gui.Btn;
import net.nicguzzo.wands.client.gui.CycleToggle;
import net.nicguzzo.wands.client.gui.Section;
import net.nicguzzo.wands.client.gui.Tabs;
import net.nicguzzo.wands.client.gui.Wdgt;
import net.nicguzzo.wands.client.screens.WandScreen;
import net.nicguzzo.wands.items.WandItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WandScreen.class)
public abstract class WandScreenAe2TabMixin {
    @Shadow
    private ItemStack wandStack;

    @Shadow
    private Vector<Wdgt> wdgets;

    @Shadow
    private Section modeOptionsSection;

    @Shadow
    private Section toolsSection;

    @Shadow
    private Tabs modeTabs;

    @Unique
    private Tabs wandsae2$ae2Tabs;

    @Unique
    private Section wandsae2$ae2Section;

    @Unique
    private boolean wandsae2$ae2TabSelected;

    @Inject(method = "init", at = @At("TAIL"))
    private void wandsae2$initAe2Tab(CallbackInfo ci) {
        if (wdgets == null || modeTabs == null || modeOptionsSection == null) {
            return;
        }

        wandsae2$ae2Tabs = new Tabs();
        wandsae2$ae2Tabs.x = modeTabs.x;
        wandsae2$ae2Tabs.y = modeTabs.y + modeTabs.height + 4;

        Btn ae2Tab = new Btn(28, 16, Component.literal("AE2"), (x, y) -> wandsae2$selectAe2Tab(true));
        ae2Tab.centerText = true;
        ae2Tab.withTooltip(Component.literal("AE2"),
                Component.literal("Configure Applied Energistics autocrafting for this wand."));
        wandsae2$ae2Tabs.add(ae2Tab);

        wandsae2$ae2Section = new Section();
        wandsae2$ae2Section.x = modeOptionsSection.x;
        wandsae2$ae2Section.y = modeOptionsSection.y;
        wandsae2$ae2Section.width = modeOptionsSection.width;

        CycleToggle<Boolean> autocraftToggle = CycleToggle.ofBoolean(
                Component.literal("Missing blocks"),
                () -> Ae2WandSettings.isAutoCraftWithoutAsking(wandsae2$getHeldWand()),
                value -> {
                    ItemStack stack = wandsae2$getHeldWand();
                    Ae2WandSettings.setAutoCraftWithoutAsking(stack, value);
                    PacketDistributor.sendToServer(new SetAutoCraftModePacket(value));
                },
                "Auto",
                "Ask");
        autocraftToggle.width = modeOptionsSection.width;
        autocraftToggle.withTooltip(Component.literal("Missing blocks"),
                Component.literal("Ask before AE2 autocrafts missing blocks, or start the craft automatically."));
        wandsae2$ae2Section.add(autocraftToggle);
        wandsae2$ae2Section.layout();
        wandsae2$ae2Section.recalculateBounds();
        wandsae2$ae2Section.visible = false;

        wdgets.add(wandsae2$ae2Section);
        wdgets.add(wandsae2$ae2Tabs);
    }

    @Inject(method = "update_selections", at = @At("TAIL"))
    private void wandsae2$updateAe2TabSelection(CallbackInfo ci) {
        wandsae2$applyAe2TabSelection();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void wandsae2$leaveAe2TabOnMainTabClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (modeTabs != null && modeTabs.inside((int) mouseX, (int) mouseY)) {
            wandsae2$selectAe2Tab(false);
        }
    }

    @Unique
    private void wandsae2$selectAe2Tab(boolean selected) {
        wandsae2$ae2TabSelected = selected;
        wandsae2$applyAe2TabSelection();
    }

    @Unique
    private void wandsae2$applyAe2TabSelection() {
        if (wandsae2$ae2Section == null || wandsae2$ae2Tabs == null) {
            return;
        }

        boolean linked = wandsae2$isLinkedToAe2();
        wandsae2$ae2Tabs.visible = linked;
        wandsae2$ae2Section.visible = linked && wandsae2$ae2TabSelected;
        wandsae2$ae2Tabs.selected = wandsae2$ae2TabSelected ? 0 : -1;
        if (linked && wandsae2$ae2TabSelected) {
            if (modeOptionsSection != null) {
                modeOptionsSection.visible = false;
            }
            if (toolsSection != null) {
                toolsSection.visible = false;
            }
            if (modeTabs != null) {
                modeTabs.selected = -1;
            }
        }
    }

    @Unique
    private boolean wandsae2$isLinkedToAe2() {
        return wandsae2$getHeldWand().has(AEComponents.WIRELESS_LINK_TARGET);
    }

    @Unique
    private ItemStack wandsae2$getHeldWand() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = minecraft.player.getItemInHand(hand);
                if (stack.getItem() instanceof WandItem) {
                    return stack;
                }
            }
        }
        return wandStack == null ? ItemStack.EMPTY : wandStack;
    }
}
