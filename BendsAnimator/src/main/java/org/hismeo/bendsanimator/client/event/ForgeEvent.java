package org.hismeo.bendsanimator.client.event;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.bendsanimator.client.BendsAnimator;
import org.hismeo.bendsanimator.client.util.BendsUtil;

@Mod.EventBusSubscriber(modid = BendsAnimator.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeEvent {
    @SubscribeEvent
    public static void testAnimator(ClientChatEvent event){
        if (event.getOriginalMessage().equals("attack")){
            BendsUtil.getModel(Minecraft.getInstance().player);
        }
    }
}
