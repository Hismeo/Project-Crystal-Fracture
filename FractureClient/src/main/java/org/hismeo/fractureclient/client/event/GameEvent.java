package org.hismeo.fractureclient.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.control.BlockCullController;
import org.hismeo.fractureclient.client.control.CameraRotateController;
import org.hismeo.fractureclient.client.init.KeyInit;
import org.hismeo.fractureclient.client.render.gui.CustomDebugMessage;
import org.hismeo.fractureclient.client.render.screen.ThemeScreen;

@EventBusSubscriber(modid = FractureClient.MODID, value = Dist.CLIENT)
public class GameEvent {
    @SubscribeEvent
    public static void angle(ViewportEvent.ComputeCameraAngles event) {
        event.setPitch(OrthographicCameraConfig.pitch);
        event.setYaw(CameraRotateController.getRenderYaw());
    }

    @SubscribeEvent
    public static void title(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            event.addListener(Button.builder(Component.empty(), b->Minecraft.getInstance().setScreen(new ThemeScreen())).size(20, 20).pos(0, 0).build());
        }
    }

    @SubscribeEvent
    public static void debugMessage(RenderGuiLayerEvent.Post event) {
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        CameraRotateController.render(guiGraphics, minecraft);
        CustomDebugMessage.render(guiGraphics, minecraft);
    }

    @SubscribeEvent
    public static void renderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        BlockCullController.renderDebugCullBoxWorld(
                event.getPoseStack(),
                event.getCamera().getPosition(),
                minecraft.renderBuffers().bufferSource(),
                minecraft
        );
    }

    @SubscribeEvent
    public static void keyRegister(RegisterKeyMappingsEvent event) {
        event.register(KeyInit.ROTATE_CAMERA);
    }

    @SubscribeEvent
    public static void keyTrigger(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        CameraRotateController.tick(minecraft, KeyInit.ROTATE_CAMERA.isDown());
        BlockCullController.tick(Minecraft.getInstance());
    }
}
