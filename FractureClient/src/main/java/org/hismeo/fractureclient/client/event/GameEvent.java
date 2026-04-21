package org.hismeo.fractureclient.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.fractureclient.client.init.KeyInit;
import org.hismeo.fractureclient.client.render.gui.CustomDebugMessage;

@EventBusSubscriber(modid = FractureClient.MODID, value = Dist.CLIENT)
public class GameEvent {
    //TODO 交互分离
    @SubscribeEvent
    public static void angle(ViewportEvent.ComputeCameraAngles event) {
        //TODO 可控
        event.setPitch(25);
        event.setYaw(0);
    }

    @SubscribeEvent
    public static void debugMessage(RenderGuiLayerEvent.Post event) {
        GuiGraphics guiGraphics = event.getGuiGraphics();
        for (int i = 0; i < CustomDebugMessage.list.size(); i++) {
            guiGraphics.drawString(Minecraft.getInstance().font, CustomDebugMessage.list.get(i), 0, i * 10, 0xFFFFFFFF);
        }
        CustomDebugMessage.list.clear();
    }

    @SubscribeEvent
    public static void keyRegister(RegisterKeyMappingsEvent event) {
        event.register(KeyInit.ROTATE_CAMERA);
    }

    @SubscribeEvent
    public static void keyTrigger(ClientTickEvent.Pre event) {

    }
}
