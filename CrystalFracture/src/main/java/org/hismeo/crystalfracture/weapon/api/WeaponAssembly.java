package org.hismeo.crystalfracture.weapon.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record WeaponAssembly(
        WeaponSchemaId schema,
        Map<WeaponSlotId, WeaponPartId> parts
) {
    public WeaponAssembly {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(parts, "parts");
        parts = Collections.unmodifiableMap(new TreeMap<>(parts));
    }
}
