package org.hismeo.crystalfracture.weapon.internal.reload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystalfracture.weapon.WeaponAuthority;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistryManager;

@EventBusSubscriber(modid = CrystalFracture.MODID)
public final class WeaponServerEvents {
    private WeaponServerEvents() {
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new WeaponResourceReloadListener(WeaponRegistryManager.instance()));
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WeaponAuthority.ensureAndSync(player);
        }
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WeaponAuthority.ensureAndSync(player);
        }
    }
}
