package org.hismeo.mapshow;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(MapShow.MODID)
public class MapShow {
    public static final String MODID = "map_show";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public MapShow(){
        IEventBus modBusEvent = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
