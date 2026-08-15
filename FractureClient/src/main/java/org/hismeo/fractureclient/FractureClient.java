package org.hismeo.fractureclient;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystallib.api.config.CrystalConfigApi;
import org.hismeo.actionguide.ActionGuide;
import org.hismeo.fractureclient.client.avatar.PlayerActionAnimationBridge;
import org.hismeo.fractureclient.client.config.OctopathVisualConfig;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.avatar.PlayerAvatarExtension;
import org.hismeo.fractureclient.client.weapon.PlayerWeaponExtension;
import org.hismeo.fractureclient.client.weapon.WeaponPreviewRenderExtension;
import org.hismeo.fractureclient.client.render.octopath.OctopathVisualExtension;
import org.hismeo.crystalfracture.weapon.internal.registry.BundledWeaponDefinitions;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistryManager;
import org.hismeo.haikalathost.api.client.advanced.event.RegisterHaikalatExtensionsEvent;
import org.hismeo.haikalathost.api.event.RegisterHaikalatContentEvent;
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
        long weaponGeneration = BundledWeaponDefinitions.publishIfEmpty(WeaponRegistryManager.instance());
        LOGGER.info("Client weapon definitions ready at generation {}", weaponGeneration);
        CrystalConfigApi.register(OrthographicCameraConfig.class);
        CrystalConfigApi.register(OctopathVisualConfig.class);
        PlayerActionAnimationBridge.install(ActionGuide.api());
        iEventBus.addListener(FractureClient::registerHaikalatContent);
        iEventBus.addListener(FractureClient::registerHaikalatExtensions);
    }

    static void registerHaikalatContent(RegisterHaikalatContentEvent event) {
        // Weapon visuals are owned by CrystalFracture while this client mod performs the GL load.
        event.namespaces().register(CrystalFracture.MODID);
    }

    private static void registerHaikalatExtensions(RegisterHaikalatExtensionsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(MODID, "player_avatar"), new PlayerAvatarExtension());
        event.register(ResourceLocation.fromNamespaceAndPath(MODID, "player_weapon"), new PlayerWeaponExtension());
        event.register(ResourceLocation.fromNamespaceAndPath(MODID, "octopath_look"), new OctopathVisualExtension());
        // Preview depth is orthographic, so draw it after the world-space depth-of-field pass.
        event.register(ResourceLocation.fromNamespaceAndPath(MODID, "weapon_preview"), new WeaponPreviewRenderExtension());
    }
}
