package org.hismeo.hazedungeon;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.hismeo.hazedungeon.worldgen.structure.ModStructures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HazeDungeon.MODID)
public class HazeDungeon {
    public static final String MODID = "haze_dungeon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public HazeDungeon(IEventBus iEventBus, ModContainer modContainer){
        ModStructures.PIECE_TYPES.register(iEventBus);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
