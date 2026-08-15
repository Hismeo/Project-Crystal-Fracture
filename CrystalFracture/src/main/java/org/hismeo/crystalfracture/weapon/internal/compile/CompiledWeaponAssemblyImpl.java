package org.hismeo.crystalfracture.weapon.internal.compile;

import org.hismeo.crystalfracture.weapon.api.CompiledWeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.CompiledWeaponConnection;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.ResolvedMarker;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CompiledWeaponAssemblyImpl implements CompiledWeaponAssembly {
    private final WeaponSchemaId schemaId;
    private final WeaponSlotId root;
    private final Map<WeaponSlotId, WeaponPartDefinition> parts;
    private final List<CompiledWeaponConnection> parentFirstConnections;
    private final Map<MarkerName, ResolvedMarker> markers;
    private final String contentHash;

    CompiledWeaponAssemblyImpl(
            WeaponSchemaId schemaId,
            WeaponSlotId root,
            Map<WeaponSlotId, WeaponPartDefinition> parts,
            List<CompiledWeaponConnection> parentFirstConnections,
            Map<MarkerName, ResolvedMarker> markers,
            String contentHash
    ) {
        this.schemaId = Objects.requireNonNull(schemaId, "schemaId");
        this.root = Objects.requireNonNull(root, "root");
        this.parts = Collections.unmodifiableMap(new LinkedHashMap<>(parts));
        this.parentFirstConnections = List.copyOf(parentFirstConnections);
        this.markers = Collections.unmodifiableMap(new java.util.TreeMap<>(markers));
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
    }

    @Override
    public WeaponSchemaId schemaId() {
        return schemaId;
    }

    @Override
    public WeaponSlotId root() {
        return root;
    }

    @Override
    public Map<WeaponSlotId, WeaponPartDefinition> parts() {
        return parts;
    }

    @Override
    public List<CompiledWeaponConnection> parentFirstConnections() {
        return parentFirstConnections;
    }

    @Override
    public Map<MarkerName, ResolvedMarker> markers() {
        return markers;
    }

    @Override
    public String contentHash() {
        return contentHash;
    }
}
