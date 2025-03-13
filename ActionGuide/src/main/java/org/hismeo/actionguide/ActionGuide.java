package org.hismeo.actionguide;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(ActionGuide.MODID)
public class ActionGuide {
    public static final String MODID = "action_guide";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public ActionGuide(){
        IEventBus modBusEvent = FMLJavaModLoadingContext.get().getModEventBus();
    }
}
