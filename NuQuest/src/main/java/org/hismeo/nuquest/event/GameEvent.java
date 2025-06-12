package org.hismeo.nuquest.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.core.data.dialog.DialogConfigLoader;
import org.hismeo.nuquest.core.data.dialog.DialogLoader;

@EventBusSubscriber(modid = NuQuest.MODID, bus = EventBusSubscriber.Bus.GAME)
public class GameEvent {
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event){
    }

    @SubscribeEvent
    public static void dataListener(AddReloadListenerEvent event){
        event.addListener(new DialogLoader());
//        event.addListener(new DialogConfigLoader());
    }
}
