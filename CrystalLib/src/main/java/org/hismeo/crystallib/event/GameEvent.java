package org.hismeo.crystallib.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.hismeo.crystallib.CrystalLib;
import org.hismeo.crystallib.common.command.CrystalConfigCommand;

@EventBusSubscriber(modid = CrystalLib.MODID)
public class GameEvent {
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        CrystalConfigCommand.register(event.getDispatcher());
    }
}
