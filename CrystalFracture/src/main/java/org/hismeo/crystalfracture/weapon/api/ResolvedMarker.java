package org.hismeo.crystalfracture.weapon.api;

import java.util.Objects;

public record ResolvedMarker(
        MarkerName exportName,
        WeaponSlotId slot,
        WeaponPartId part,
        MarkerName partMarker,
        String nodeName
) {
    public ResolvedMarker {
        Objects.requireNonNull(exportName, "exportName");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(partMarker, "partMarker");
        Objects.requireNonNull(nodeName, "nodeName");
        if (nodeName.isBlank()) {
            throw new IllegalArgumentException("resolved marker node must not be blank");
        }
    }
}
