package org.hismeo.crystalfracture.weapon.definition;

import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;

import java.util.Objects;

public record WeaponMarkerExport(WeaponSlotId slot, MarkerName marker) {
    public WeaponMarkerExport {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(marker, "marker");
    }
}
