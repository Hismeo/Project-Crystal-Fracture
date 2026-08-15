package org.hismeo.fractureclient.client.weapon;

import com.kaleblangley.haikalat.subsystems.render3d.MeshRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.hismeo.crystalfracture.weapon.WeaponAuthority;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.fractureclient.client.avatar.PlayerAvatarExtension;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/** Binds one visual assembly instance to each animated player hand socket. */
final class PlayerWeaponManager implements AutoCloseable {
    private final BiFunction<AbstractClientPlayer, WeaponAssembly, WeaponPartVisualCatalog> catalogs;
    private final Map<UUID, Binding> bindings = new HashMap<>();
    private boolean closed;

    PlayerWeaponManager(BiFunction<AbstractClientPlayer, WeaponAssembly, WeaponPartVisualCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    void update(ClientLevel level) {
        if (closed) {
            return;
        }
        Set<UUID> present = new HashSet<>();
        Matrix4f socket = new Matrix4f();
        for (AbstractClientPlayer player : level.players()) {
            if (player.isRemoved() || player.isInvisible()) {
                continue;
            }
            UUID playerId = player.getUUID();
            present.add(playerId);
            Binding binding = bindings.get(playerId);
            var authoritative = WeaponAuthority.current(player);
            if (authoritative.isEmpty()) {
                if (binding != null) {
                    binding.rendered().socketUnavailable();
                }
                continue;
            }
            WeaponAssembly assembly = authoritative.orElseThrow();
            WeaponPartVisualCatalog catalog = catalogs.apply(player, assembly);
            if (catalog == null) {
                if (binding != null) {
                    binding.rendered().socketUnavailable();
                }
                continue;
            }
            if (binding != null
                    && (!binding.assembly().equals(assembly) || binding.catalog() != catalog)) {
                binding.rendered().close();
                bindings.remove(playerId);
                binding = null;
            }
            if (!PlayerAvatarExtension.weaponSocket(playerId, socket)) {
                if (binding != null) {
                    binding.rendered().socketUnavailable();
                }
                continue;
            }
            if (binding == null) {
                binding = new Binding(assembly, catalog, new WeaponRenderedAssembly(catalog));
                bindings.put(playerId, binding);
            }
            Matrix4f weaponRoot = new Matrix4f();
            catalog.weaponRootAtSocket(socket, weaponRoot);
            binding.rendered().updateSocket(weaponRoot);
        }
        bindings.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().rendered().close();
            return true;
        });
    }

    List<MeshRenderer> readyRenderers() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID hiddenLocalPlayer = minecraft.player != null
                && minecraft.options.getCameraType().isFirstPerson()
                ? minecraft.player.getUUID()
                : null;
        List<MeshRenderer> result = new ArrayList<>();
        bindings.forEach((playerId, binding) -> {
            if (binding.rendered().ready() && !playerId.equals(hiddenLocalPlayer)) {
                result.addAll(binding.rendered().renderers());
            }
        });
        return result;
    }

    boolean markerWorld(UUID playerId, MarkerName marker, Matrix4f destination) {
        Binding binding = bindings.get(playerId);
        return binding != null && binding.rendered().markerWorld(marker, destination);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        bindings.values().forEach(binding -> binding.rendered().close());
        bindings.clear();
    }

    private record Binding(
            WeaponAssembly assembly,
            WeaponPartVisualCatalog catalog,
            WeaponRenderedAssembly rendered
    ) {
    }
}
