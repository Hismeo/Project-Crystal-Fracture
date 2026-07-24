package org.hismeo.haikalathost;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.hismeo.haikalathost.client.backend.FullTakeoverConfiguration;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = HaikalatHost.MOD_ID, dist = Dist.CLIENT)
public final class HaikalatHost {
    public static final String MOD_ID = "haikalat_host";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public HaikalatHost(IEventBus modEventBus, ModContainer modContainer) {
        MinecraftRuntimeLifecycle.register(modEventBus);
        LOGGER.info("Haikalat Host initialized");
        FullTakeoverConfiguration configuration = FullTakeoverConfiguration.current();
        LOGGER.info("Haikalat Host backend={}, validation={}, gpuDriven={}, bindless={}",
                configuration.backend(), configuration.validation(),
                configuration.gpuDriven(), configuration.bindless());

    }
}
