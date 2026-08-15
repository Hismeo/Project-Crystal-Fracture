package org.hismeo.crystalfracture.weapon;

import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartLoader;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartTypeLoader;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponSchemaLoader;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponDefinitionRegistry;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistrySnapshot;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeaponTestFixtures {
    private WeaponTestFixtures() {
    }

    static WeaponRegistrySnapshot standardSwordRegistry() throws Exception {
        WeaponPartTypeLoader typeLoader = new WeaponPartTypeLoader();
        List<WeaponPartTypeDefinition> types = new ArrayList<>();
        for (String name : List.of(
                "sword_grip", "golden_sword_guard", "golden_sword_blade")) {
            WeaponPartTypeId id = WeaponPartTypeId.parse("crystal_fracture:" + name);
            try (Reader json = resource(
                    "/data/crystal_fracture/crystal_fracture/weapon_part_types/" + name + ".json")) {
                types.add(typeLoader.load(id, json).value());
            }
        }

        WeaponPartLoader partLoader = new WeaponPartLoader();
        List<WeaponPartDefinition> parts = new ArrayList<>();
        for (String name : List.of("oak_grip", "iron_crossguard", "steel_blade")) {
            WeaponPartId id = WeaponPartId.parse("crystal_fracture:" + name);
            try (Reader json = resource(
                    "/data/crystal_fracture/crystal_fracture/weapon_parts/" + name + ".json")) {
                parts.add(partLoader.load(id, json).value());
            }
        }

        WeaponSchemaDefinition schema;
        try (Reader json = resource(
                "/data/crystal_fracture/crystal_fracture/weapon_schemas/standard_sword.json")) {
            schema = new WeaponSchemaLoader().load(
                    WeaponSchemaId.parse("crystal_fracture:standard_sword"), json).value();
        }
        var result = new WeaponDefinitionRegistry().build(types, parts, List.of(schema));
        assertTrue(result.valid(), () -> "unexpected fixture problems: " + result.problems());
        return result.snapshot().orElseThrow();
    }

    static WeaponAssembly standardSwordAssembly() {
        return new WeaponAssembly(
                WeaponSchemaId.parse("crystal_fracture:standard_sword"),
                Map.of(
                        new WeaponSlotId("grip"), WeaponPartId.parse("crystal_fracture:oak_grip"),
                        new WeaponSlotId("crossguard"), WeaponPartId.parse("crystal_fracture:iron_crossguard"),
                        new WeaponSlotId("blade"), WeaponPartId.parse("crystal_fracture:steel_blade")
                ));
    }

    static Reader resource(String path) {
        var stream = WeaponTestFixtures.class.getResourceAsStream(path);
        assertNotNull(stream, "missing fixture " + path);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
