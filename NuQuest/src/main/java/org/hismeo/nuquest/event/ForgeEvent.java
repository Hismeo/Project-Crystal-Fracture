package org.hismeo.nuquest.event;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.core.data.dialog.DialogLoader;

@Mod.EventBusSubscriber(modid = NuQuest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvent {
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event){
    }

    @SubscribeEvent
    public static void dataListener(AddReloadListenerEvent event){
        event.addListener(new DialogLoader());
    }
}
