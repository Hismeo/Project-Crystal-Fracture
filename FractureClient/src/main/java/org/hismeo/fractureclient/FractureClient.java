package org.hismeo.fractureclient;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.hismeo.crystallib.api.config.CrystalConfigApi;
import org.hismeo.fractureclient.client.config.OctopathVisualConfig;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.render.octopath.OctopathVisualExtension;
import org.hismeo.haikalathost.api.client.advanced.event.RegisterHaikalatExtensionsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
 *TODO 下雨渲染
 *TODO 入水特效
 *TODO 配置统一调试
 * */
@Mod(value = FractureClient.MODID, dist = Dist.CLIENT)
public class FractureClient {
    public static final String MODID = "fracture_client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public FractureClient(IEventBus iEventBus, ModContainer modContainer) {
        CrystalConfigApi.register(OrthographicCameraConfig.class);
        CrystalConfigApi.register(OctopathVisualConfig.class);
        iEventBus.addListener(FractureClient::registerHaikalatExtensions);
    }

    private static void registerHaikalatExtensions(RegisterHaikalatExtensionsEvent event) {
        event.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        MODID,
                        "octopath_look"),
                new OctopathVisualExtension());
    }
}
