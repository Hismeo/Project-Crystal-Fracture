package org.hismeo.crystalfracture.weapon.definition;

import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;

import java.util.Objects;

public record WeaponSlotDefinition(WeaponPartTypeId partType) {
    public WeaponSlotDefinition {
        Objects.requireNonNull(partType, "partType");
    }
}
