package org.hismeo.crystallib.client.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.hismeo.crystallib.CrystalLib;


@Mod.EventBusSubscriber(modid = CrystalLib.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeEvent {

}
