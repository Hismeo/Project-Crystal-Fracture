package org.hismeo.crystalfracture;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.hismeo.mapshow.MapShow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(CrystalFracture.MODID)
public class CrystalFracture {
    public static final String MODID = "crystal_fracture";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public CrystalFracture(){
        IEventBus modBusEvent = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
