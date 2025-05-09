package org.hismeo.nuquest.event.client;

import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.common.command.DialogueCommand;

@Mod.EventBusSubscriber(modid = NuQuest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvent {
    @SubscribeEvent
    public static void registerCommand(RegisterClientCommandsEvent event){
        DialogueCommand.register(event.getDispatcher());
    }
}