package org.hismeo.fractureclient.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.fractureclient.client.command.BlockScanDebugCommand;
import org.hismeo.fractureclient.client.command.RoomRegionCommand;
import org.hismeo.fractureclient.client.control.BlockCullController;
import org.hismeo.fractureclient.client.control.CameraModeController;
import org.hismeo.fractureclient.client.control.CameraRotateController;
import org.hismeo.fractureclient.client.control.FloorAwareCameraController;
import org.hismeo.fractureclient.client.control.RoomSelectionController;
import org.hismeo.fractureclient.client.init.KeyInit;
import org.hismeo.fractureclient.client.render.BlockCullTransitionRenderer;
import org.hismeo.fractureclient.client.render.RoomSelectionRenderer;
import org.hismeo.fractureclient.client.render.gui.DebugMessage;
import org.hismeo.fractureclient.client.render.screen.ThemeScreen;

@EventBusSubscriber(modid = FractureClient.MODID, value = Dist.CLIENT)
public class GameEvent {
    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        BlockScanDebugCommand.register(event.getDispatcher());
        RoomRegionCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void angle(ViewportEvent.ComputeCameraAngles event) {
        if (!CameraModeController.isOrthographic()) {
            return;
        }
        event.setPitch(FloorAwareCameraController.getRenderPitch());
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
        RoomSelectionController.renderHud(guiGraphics, minecraft);
        if (!CameraModeController.isOrthographic()) {
            DebugMessage.clear();
            return;
        }
        CameraRotateController.render(guiGraphics, minecraft);
        DebugMessage.render(guiGraphics, minecraft);
    }

    @SubscribeEvent
    public static void renderLevelStage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (CameraModeController.isOrthographic()) {
                BlockCullController.renderDebugCullBoxWorld(
                        event.getPoseStack(),
                        event.getCamera().getPosition(),
                        minecraft.renderBuffers().bufferSource(),
                        minecraft
                );
            }
            RoomSelectionRenderer.render(event, minecraft);
            return;
        }
        if (!CameraModeController.isOrthographic()) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            BlockCullTransitionRenderer.render(event, minecraft);
        }
    }

    @SubscribeEvent
    public static void keyRegister(RegisterKeyMappingsEvent event) {
        event.register(KeyInit.ROTATE_CAMERA);
        event.register(KeyInit.ROOM_EDITOR);
        event.register(KeyInit.ROOM_EDITOR_CANCEL);
        event.register(KeyInit.ROOM_EDITOR_UNDO);
        event.register(KeyInit.ROOM_EDITOR_HEIGHT);
    }

    @SubscribeEvent
    public static void roomEditorInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!RoomSelectionController.isActive()
                || (!event.isAttack() && !event.isUseItem() && !event.isPickBlock())) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isUseItem() && event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (event.isAttack()) {
            RoomSelectionController.handleAttack(minecraft);
        } else if (event.isUseItem()) {
            RoomSelectionController.handleUse(minecraft);
        } else {
            RoomSelectionController.undo(minecraft);
        }
    }

    @SubscribeEvent
    public static void keyTrigger(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        RoomSelectionController.tick(minecraft);
        CameraModeController.tick(minecraft);
        CameraRotateController.tick(minecraft, KeyInit.ROTATE_CAMERA.isDown());
        BlockCullController.tick(minecraft);
        BlockScanDebugCommand.tick(minecraft);
    }

    @SubscribeEvent
    public static void enforceCameraModeAfterTick(ClientTickEvent.Post event) {
        CameraModeController.enforceCameraType(Minecraft.getInstance());
    }
}
