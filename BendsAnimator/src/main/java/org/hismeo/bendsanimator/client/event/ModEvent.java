package org.hismeo.bendsanimator.client.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.bendsanimator.client.BendsAnimator;

@Mod.EventBusSubscriber(modid = BendsAnimator.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModEvent {
}
