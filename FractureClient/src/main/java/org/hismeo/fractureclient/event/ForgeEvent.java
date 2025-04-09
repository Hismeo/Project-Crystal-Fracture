package org.hismeo.fractureclient.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.fractureclient.FractureClient;

import static org.hismeo.crystallib.client.util.MinecraftUtil.getMinecraft;
import static org.hismeo.crystallib.client.util.render.BackGroundUtil.renderBlurredBackground;

@Mod.EventBusSubscriber(modid = FractureClient.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeEvent {
    @SubscribeEvent
    public static void screenEventRenderPost(ScreenEvent.Render.Post event) {
//        renderBlurredBackground(getMinecraft().getPartialTick());
    }

    @SubscribeEvent
    public static void screenEventRenderPre(ScreenEvent.Render.Pre event) {
//        renderBlurredBackground(getMinecraft().getPartialTick());
    }

    @SubscribeEvent
    public static void screenEventRenderInventoryMobEffects(ScreenEvent.RenderInventoryMobEffects event) {
//        renderBlurredBackground(getMinecraft().getPartialTick());
    }

    @SubscribeEvent
    public static void screenEventInitPre(ScreenEvent.Init.Pre event) {
//        renderBlurredBackground(getMinecraft().getPartialTick());
    }

    @SubscribeEvent
    public static void screenEventInitPost(ScreenEvent.Init.Post event) {
//        renderBlurredBackground(getMinecraft().getPartialTick());
    }

    @SubscribeEvent
    public static void screenEventClosing(ScreenEvent.Closing event) {
//        renderBlurredBackground(getMinecraft().getPartialTick());
    }

    @SubscribeEvent
    public static void screenEventOpening(ScreenEvent.Opening event) {
//        renderBlurredBackground(getMinecraft().getPartialTick());
    }

    @SubscribeEvent
    public static void screenEventBackgroundRendered(ScreenEvent.BackgroundRendered event) {
//        renderBlurredBackground(getMinecraft().getPartialTick());
    }
}
