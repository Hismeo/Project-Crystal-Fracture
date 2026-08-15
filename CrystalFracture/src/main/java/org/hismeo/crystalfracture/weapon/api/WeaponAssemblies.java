package org.hismeo.crystalfracture.weapon.api;

import java.util.Map;

/** Stable built-in selections used when a player has no persisted authoritative weapon yet. */
public final class WeaponAssemblies {
    public static final WeaponAssembly DEFAULT_SWORD = new WeaponAssembly(
            WeaponSchemaId.parse("crystal_fracture:sword"),
            Map.of(
                    new WeaponSlotId("handle"), WeaponPartId.parse("crystal_fracture:wood_shaft"),
                    new WeaponSlotId("crossguard"), WeaponPartId.parse("crystal_fracture:crossguard"),
                    new WeaponSlotId("blade"), WeaponPartId.parse("crystal_fracture:sword")));

    private WeaponAssemblies() {
    }
}
