package org.hismeo.bendsanimator.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.hismeo.bendsanimator.client.BendsAnimator;
import org.hismeo.bendsanimator.client.util.ModelUtil;
import org.joml.Vector3f;

@EventBusSubscriber(modid = BendsAnimator.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ForgeEvent {
    @SubscribeEvent
    public static void testAnimator(ClientChatEvent event) {
        if (event.getOriginalMessage().equals("attack")){
            Minecraft.getInstance().player.yHeadRot = 0;
//            ModelPart modelPart = ModelUtil.getEntityModelPart(Minecraft.getInstance().player);
//            modelPart.getChild("head").offsetScale(new Vector3f(4f));
        }
    }

    @SubscribeEvent
    public static void hitEntity(LivingIncomingDamageEvent event){
        ModelPart modelPart = ModelUtil.getEntityModelPart(event.getEntity());
        if (modelPart.hasChild("right_arm")){
            modelPart.getChild("right_arm").xRot = 10;
        }
    }
}
