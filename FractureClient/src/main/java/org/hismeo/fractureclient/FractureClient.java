package org.hismeo.fractureclient;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(FractureClient.MODID)
public class FractureClient {
    public static final String MODID = "fracture_client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public FractureClient(IEventBus iEventBus, ModContainer modContainer){
    }
}
