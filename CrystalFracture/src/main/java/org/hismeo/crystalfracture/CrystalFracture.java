package org.hismeo.crystalfracture;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hismeo.actionguide.ActionGuide;
import org.hismeo.crystalfracture.dash.DashActionController;
import org.hismeo.crystalfracture.dash.DashNetwork;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaRegistry;
import org.hismeo.crystalfracture.weapon.WeaponAttachments;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistryManager;



@Mod(CrystalFracture.MODID)
public class CrystalFracture {
    public static final String MODID = "crystal_fracture";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    private static final WeaponSchemaRegistry WEAPON_REGISTRY = WeaponRegistryManager.instance();
    public CrystalFracture(IEventBus iEventBus, ModContainer modContainer){
        WeaponAttachments.register(iEventBus);
        DashActionController.install(ActionGuide.api());
        iEventBus.addListener(DashNetwork::register);
    }

    public static ResourceLocation packRL(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static WeaponSchemaRegistry weaponSchemas() {
        return WEAPON_REGISTRY;
    }

}
