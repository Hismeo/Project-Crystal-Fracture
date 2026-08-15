package org.hismeo.crystalfracture.weapon.definition;

import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record WeaponPartTypeDefinition(
        WeaponPartTypeId id,
        Set<ConnectName> requiredConnects,
        Set<MarkerName> requiredMarkers
) {
    public WeaponPartTypeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requiredConnects, "requiredConnects");
        Objects.requireNonNull(requiredMarkers, "requiredMarkers");
        requiredConnects = Collections.unmodifiableSet(new TreeSet<>(requiredConnects));
        requiredMarkers = Collections.unmodifiableSet(new TreeSet<>(requiredMarkers));
    }
}
