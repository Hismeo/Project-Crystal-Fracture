package org.hismeo.nuquest.event.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.common.command.DialogCommand;

@EventBusSubscriber(modid = NuQuest.MODID, value = Dist.CLIENT)
public class GameEvent {
    @SubscribeEvent
    public static void registerCommand(RegisterClientCommandsEvent event){
        DialogCommand.register(event.getDispatcher());
    }
}