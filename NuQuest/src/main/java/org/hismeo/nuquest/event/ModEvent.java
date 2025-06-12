package org.hismeo.nuquest.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.common.network.c2s.CommandPacketC2S;
import org.hismeo.nuquest.common.network.s2c.DialogShowPacketS2C;

@EventBusSubscriber(modid = NuQuest.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModEvent {
    @SubscribeEvent
    public static void registerNetwork(RegisterPayloadHandlersEvent event){
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CommandPacketC2S.TYPE, CommandPacketC2S.STREAM_CODEC, CommandPacketC2S::handle);
        registrar.playToClient(DialogShowPacketS2C.TYPE, DialogShowPacketS2C.STREAM_CODEC, DialogShowPacketS2C::handle);
    }
}
