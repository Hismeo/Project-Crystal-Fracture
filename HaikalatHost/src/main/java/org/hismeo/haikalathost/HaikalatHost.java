package org.hismeo.haikalathost;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.hismeo.haikalathost.internal.runtime.ClientLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = HaikalatHost.MOD_ID, dist = Dist.CLIENT)
public final class HaikalatHost {
    public static final String MOD_ID = "haikalat_host";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @SuppressWarnings("FieldCanBeLocal")
    private final ClientLifecycleHooks lifecycleHooks;

    public HaikalatHost(IEventBus modEventBus) {
        lifecycleHooks = new ClientLifecycleHooks();
        lifecycleHooks.register(modEventBus);
    }
}
