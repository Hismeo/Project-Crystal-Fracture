package org.hismeo.crystalfracture.weapon.definition;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record WeaponPartDefinition(
        WeaponPartId id,
        WeaponPartTypeId type,
        ResourceLocation visualModel,
        Map<ConnectName, String> connects,
        Map<MarkerName, String> markers
) {
    public WeaponPartDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(visualModel, "visualModel");
        Objects.requireNonNull(connects, "connects");
        Objects.requireNonNull(markers, "markers");
        connects = Collections.unmodifiableMap(new TreeMap<>(connects));
        markers = Collections.unmodifiableMap(new TreeMap<>(markers));
    }
}
