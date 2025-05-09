package org.hismeo.nuquest.event;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.nuquest.NuQuest;

@Mod.EventBusSubscriber(modid = NuQuest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvent {
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event){
    }
}
