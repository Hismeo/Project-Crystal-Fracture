package org.hismeo.fractureclient.client.avatar;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.hismeo.fractureclient.FractureClient;

/** Cancels vanilla rendering only while a healthy READY Avatar is taking over. */
@EventBusSubscriber(modid = FractureClient.MODID, value = Dist.CLIENT)
public final class PlayerRenderReplacement {
    private PlayerRenderReplacement() {
    }

    @SubscribeEvent
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        PlayerAvatarExtension.beginMinecraftFrame();
    }

    @SubscribeEvent
    public static void duringLevelRender(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            PlayerAvatarExtension.beginMinecraftWorldRender();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            PlayerAvatarExtension.endMinecraftWorldRender();
        }
    }

    @SubscribeEvent
    public static void beforePlayerRender(RenderPlayerEvent.Pre event) {
        if (PlayerAvatarExtension.canReplace(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
