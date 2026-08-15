package org.hismeo.crystalfracture.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystalfracture.weapon.api.WeaponAssemblies;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;

import java.util.Objects;
import java.util.Optional;

/** Server-authoritative access to persisted player assemblies. */
public final class WeaponAuthority {
    private WeaponAuthority() {
    }

    public static Optional<WeaponAssembly> current(Player player) {
        return Objects.requireNonNull(player, "player").getExistingData(WeaponAttachments.EQUIPPED);
    }

    public static Optional<String> registryHash(Player player) {
        return Objects.requireNonNull(player, "player")
                .getExistingData(WeaponAttachments.REGISTRY_HASH)
                .filter(value -> !value.isBlank());
    }

    public static WeaponAssembly ensureAndSync(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        WeaponAssembly current = player.getData(WeaponAttachments.EQUIPPED);
        if (CrystalFracture.weaponSchemas().compile(current).isEmpty()) {
            if (CrystalFracture.weaponSchemas().compile(WeaponAssemblies.DEFAULT_SWORD).isEmpty()) {
                throw new IllegalStateException("No valid default weapon exists in the published registry");
            }
            current = WeaponAssemblies.DEFAULT_SWORD;
            player.setData(WeaponAttachments.EQUIPPED, current);
        } else {
            player.syncData(WeaponAttachments.EQUIPPED);
        }
        player.setData(WeaponAttachments.REGISTRY_HASH, CrystalFracture.weaponSchemas().contentHash());
        return current;
    }

    public static void equip(ServerPlayer player, WeaponAssembly replacement) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(replacement, "replacement");
        if (CrystalFracture.weaponSchemas().compile(replacement).isEmpty()) {
            throw new IllegalArgumentException("Weapon assembly is invalid in the current server registry: " + replacement);
        }
        player.setData(WeaponAttachments.EQUIPPED, replacement);
    }
}
