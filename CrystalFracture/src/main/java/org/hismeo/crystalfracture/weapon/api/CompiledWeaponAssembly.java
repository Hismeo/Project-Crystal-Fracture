package org.hismeo.crystalfracture.weapon.api;

import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CompiledWeaponAssembly {
    WeaponSchemaId schemaId();

    WeaponSlotId root();

    Map<WeaponSlotId, WeaponPartDefinition> parts();

    List<CompiledWeaponConnection> parentFirstConnections();

    Map<MarkerName, ResolvedMarker> markers();

    default Optional<ResolvedMarker> marker(MarkerName name) {
        return Optional.ofNullable(markers().get(name));
    }

    String contentHash();
}
