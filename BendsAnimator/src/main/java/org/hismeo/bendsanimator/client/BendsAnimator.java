package org.hismeo.bendsanimator.client;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(BendsAnimator.MODID)
public class BendsAnimator {
    public static final String MODID = "bends_animator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public BendsAnimator(){
        IEventBus modBusEvent = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
