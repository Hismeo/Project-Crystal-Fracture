package org.hismeo.bendsanimator.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.bendsanimator.client.BendsAnimator;
import org.hismeo.bendsanimator.client.util.ModelUtil;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = BendsAnimator.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeEvent {
    @SubscribeEvent
    public static void testAnimator(ClientChatEvent event) {
        if (event.getOriginalMessage().equals("attack")){
            ModelPart modelPart = ModelUtil.getEntityModelPart(Minecraft.getInstance().player);
            modelPart.getChild("head").offsetScale(new Vector3f(4f));
        }
    }

    @SubscribeEvent
    public static void hitEntity(LivingHurtEvent event){
        ModelPart modelPart = ModelUtil.getEntityModelPart(event.getEntity());
        if (modelPart.hasChild("head")){
            modelPart.getChild("head").offsetScale(new Vector3f(4f));
        }
    }
}
