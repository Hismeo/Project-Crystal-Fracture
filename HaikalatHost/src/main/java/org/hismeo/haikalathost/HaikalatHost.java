package org.hismeo.haikalathost;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HaikalatHost.MOD_ID)
public final class HaikalatHost {
    public static final String MOD_ID = "haikalat_host";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public HaikalatHost(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Haikalat Host initialized");
    }
}
