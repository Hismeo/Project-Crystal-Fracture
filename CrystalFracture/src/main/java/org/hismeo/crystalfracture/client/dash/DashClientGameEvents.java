package org.hismeo.crystalfracture.client.dash;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.hismeo.actionguide.ActionGuide;
import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.action.IntentPhase;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystalfracture.dash.DashIds;

import java.util.concurrent.atomic.AtomicLong;

@EventBusSubscriber(modid = CrystalFracture.MODID, value = Dist.CLIENT)
public final class DashClientGameEvents {
    private static final AtomicLong SEQUENCES = new AtomicLong();

    private DashClientGameEvents() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (DashKeyMappings.DASH.consumeClick()) {
            if (minecraft.player == null || minecraft.getConnection() == null
                    || minecraft.screen != null || !minecraft.player.isAlive()) {
                continue;
            }
            ActionGuide.api().sendIntentToServer(new ActionIntentRequest(
                    DashIds.INTENT,
                    IntentPhase.PRESS,
                    SEQUENCES.incrementAndGet(),
                    minecraft.player.tickCount));
        }
    }
}
