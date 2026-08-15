package org.hismeo.crystalfracture.weapon.definition;

import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record WeaponSchemaDefinition(
        WeaponSchemaId id,
        int schemaVersion,
        WeaponSlotId root,
        Map<WeaponSlotId, WeaponSlotDefinition> slots,
        List<WeaponConnectionDefinition> connections,
        Map<MarkerName, WeaponMarkerExport> markers
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public WeaponSchemaDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(markers, "markers");
        slots = Collections.unmodifiableMap(new TreeMap<>(slots));
        connections = List.copyOf(connections);
        markers = Collections.unmodifiableMap(new TreeMap<>(markers));
    }
}
