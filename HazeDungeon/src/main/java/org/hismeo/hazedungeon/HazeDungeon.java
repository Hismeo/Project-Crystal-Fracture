package org.hismeo.hazedungeon;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HazeDungeon.MODID)
public class HazeDungeon {
    public static final String MODID = "haze_dungeon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public HazeDungeon(){
        IEventBus modBusEvent = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
