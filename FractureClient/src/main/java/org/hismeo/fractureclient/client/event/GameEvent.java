package org.hismeo.fractureclient.client.event;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hismeo.fractureclient.FractureClient;

@EventBusSubscriber(modid = FractureClient.MODID, value = Dist.CLIENT)
public class GameEvent {
    //TODO 交互分离
    @SubscribeEvent
    public static void angle(ViewportEvent.ComputeCameraAngles event) {
        event.setPitch(30);
        event.setYaw(400);
    }
}
