package org.hismeo.crystalfracture.weapon.api;

import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;

import java.util.Map;
import java.util.Optional;

/** Read-only view of the last complete weapon data-pack generation. */
public interface WeaponSchemaRegistry {
    long generation();

    /** Stable SHA-256 of the published logical definitions, excluding JSON object order. */
    String contentHash();

    Map<WeaponPartTypeId, WeaponPartTypeDefinition> partTypes();

    Map<WeaponPartId, WeaponPartDefinition> parts();

    Map<WeaponSchemaId, WeaponSchemaDefinition> schemas();

    Optional<WeaponPartTypeDefinition> partType(WeaponPartTypeId id);

    Optional<WeaponPartDefinition> part(WeaponPartId id);

    Optional<WeaponSchemaDefinition> schema(WeaponSchemaId id);

    /** Returns an empty result when the requested selection violates its schema. */
    Optional<CompiledWeaponAssembly> compile(WeaponAssembly assembly);
}
