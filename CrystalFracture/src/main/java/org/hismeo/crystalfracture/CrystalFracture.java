package org.hismeo.crystalfracture;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(CrystalFracture.MODID)
public class CrystalFracture {
    public static final String MODID = "crystal_fracture";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public CrystalFracture(IEventBus iEventBus, ModContainer modContainer){
    }

    public static ResourceLocation packRL(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
