package org.hismeo.crystalfracture.client.dash;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.hismeo.crystalfracture.CrystalFracture;

@EventBusSubscriber(modid = CrystalFracture.MODID, value = Dist.CLIENT)
public final class DashClientModEvents {
    private DashClientModEvents() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DashKeyMappings.DASH);
    }
}
