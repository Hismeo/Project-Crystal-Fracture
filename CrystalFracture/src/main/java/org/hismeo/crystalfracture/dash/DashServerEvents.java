package org.hismeo.crystalfracture.dash;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.hismeo.actionguide.ActionGuide;
import org.hismeo.crystalfracture.CrystalFracture;

@EventBusSubscriber(modid = CrystalFracture.MODID)
public final class DashServerEvents {
    private DashServerEvents() {
    }

    /** Runs after ActionGuide advances its authoritative cursor for this server tick. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterActionTick(ServerTickEvent.Pre event) {
        DashActionController.tick(ActionGuide.api());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        DashActionController.clear();
    }
}
