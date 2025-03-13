package org.hismeo.crystallib;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(CrystalLib.MODID)
public class CrystalLib {
    public static final String MODID = "crystal_lib";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public CrystalLib(){
        IEventBus modBusEvent = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
