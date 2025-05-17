package org.hismeo.nuquest.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.common.network.CommandPacketC2S;

@EventBusSubscriber(modid = NuQuest.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModEvent {
    @SubscribeEvent
    public static void registerNetwork(RegisterPayloadHandlersEvent event){
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CommandPacketC2S.TYPE, CommandPacketC2S.STREAM_CODEC, CommandPacketC2S::handle);
    }
}
