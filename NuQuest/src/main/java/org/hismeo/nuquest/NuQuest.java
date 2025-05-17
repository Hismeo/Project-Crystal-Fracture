package org.hismeo.nuquest;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(NuQuest.MODID)
public class NuQuest {
    public static final String MODID = "nu_quest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public NuQuest(IEventBus iEventBus, ModContainer modContainer){
    }

    public static ResourceLocation byModResource(String path){
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
