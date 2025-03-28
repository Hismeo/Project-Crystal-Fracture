package org.hismeo.hazedungeon;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.hismeo.crystallib.common.util.DevelopUtil;
import org.hismeo.hazedungeon.worldgen.structure.ModStructures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HazeDungeon.MODID)
public class HazeDungeon {
    public static final String MODID = "haze_dungeon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public HazeDungeon(){
        IEventBus modBusEvent = FMLJavaModLoadingContext.get().getModEventBus();
        ModStructures.PIECE_TYPES.register(modBusEvent);
    }

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }
}
