package org.hismeo.crystalfracture.weapon.internal.registry;

import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class WeaponRegistrySnapshot {
    private static final WeaponRegistrySnapshot EMPTY = new WeaponRegistrySnapshot(
            Map.of(), Map.of(), Map.of());
    private final Map<WeaponPartTypeId, WeaponPartTypeDefinition> partTypes;
    private final Map<WeaponPartId, WeaponPartDefinition> parts;
    private final Map<WeaponSchemaId, WeaponSchemaDefinition> schemas;

    WeaponRegistrySnapshot(
            Map<WeaponPartTypeId, WeaponPartTypeDefinition> partTypes,
            Map<WeaponPartId, WeaponPartDefinition> parts,
            Map<WeaponSchemaId, WeaponSchemaDefinition> schemas
    ) {
        this.partTypes = immutableSorted(partTypes);
        this.parts = immutableSorted(parts);
        this.schemas = immutableSorted(schemas);
    }

    public static WeaponRegistrySnapshot empty() {
        return EMPTY;
    }

    public Map<WeaponPartTypeId, WeaponPartTypeDefinition> partTypes() {
        return partTypes;
    }

    public Map<WeaponPartId, WeaponPartDefinition> parts() {
        return parts;
    }

    public Map<WeaponSchemaId, WeaponSchemaDefinition> schemas() {
        return schemas;
    }

    public Optional<WeaponPartTypeDefinition> partType(WeaponPartTypeId id) {
        return Optional.ofNullable(partTypes.get(id));
    }

    public Optional<WeaponPartDefinition> part(WeaponPartId id) {
        return Optional.ofNullable(parts.get(id));
    }

    public Optional<WeaponSchemaDefinition> schema(WeaponSchemaId id) {
        return Optional.ofNullable(schemas.get(id));
    }

    private static <K extends Comparable<K>, V> Map<K, V> immutableSorted(Map<K, V> values) {
        Objects.requireNonNull(values, "values");
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
