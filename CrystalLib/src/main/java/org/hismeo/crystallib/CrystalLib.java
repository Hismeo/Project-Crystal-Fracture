package org.hismeo.crystallib;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(CrystalLib.MODID)
public class CrystalLib {
    public static final String MODID = "crystal_lib";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public CrystalLib(IEventBus iEventBus, ModContainer modContainer){
    }
}
