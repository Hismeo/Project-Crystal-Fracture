package org.hismeo.fractureclient.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.crystallib.client.util.render.BackGroundUtil;
import org.hismeo.fractureclient.FractureClient;

import java.util.Arrays;

import static org.hismeo.crystallib.client.util.MinecraftUtil.*;

@Mod.EventBusSubscriber(modid = FractureClient.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeEvent {
    @SubscribeEvent
    public static void screenEventRenderPost(ScreenEvent.Render.Post event) {
    }

    @SubscribeEvent
    public static void screenEventRenderPre(ScreenEvent.Render.Pre event) {
    }

    @SubscribeEvent
    public static void screenEventRenderInventoryMobEffects(ScreenEvent.RenderInventoryMobEffects event) {
    }

    @SubscribeEvent
    public static void screenEventInitPre(ScreenEvent.Init.Pre event) {
    }

    @SubscribeEvent
    public static void screenEventInitPost(ScreenEvent.Init.Post event) {

    }

    @SubscribeEvent
    public static void screenEventClosing(ScreenEvent.Closing event) {
    }

    @SubscribeEvent
    public static void screenEventOpening(ScreenEvent.Opening event) {
    }

    @SubscribeEvent
    public static void screenEventBackgroundRendered(ScreenEvent.BackgroundRendered event) {
        Screen currentScreen = event.getScreen();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        BackGroundUtil.panoramaUtil(currentScreen, getPartialTick(), getMinecraft().level, guiGraphics, currentScreen.width, currentScreen.height);
    }
}
