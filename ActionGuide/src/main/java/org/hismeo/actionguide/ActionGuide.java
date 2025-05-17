package org.hismeo.actionguide;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(ActionGuide.MODID)
public class ActionGuide {
    public static final String MODID = "action_guide";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public ActionGuide(IEventBus iEventBus, ModContainer modContainer){
    }
}
