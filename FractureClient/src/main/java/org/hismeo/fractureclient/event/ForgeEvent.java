package org.hismeo.fractureclient.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.fractureclient.FractureClient;

@Mod.EventBusSubscriber(modid = FractureClient.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeEvent {
}
