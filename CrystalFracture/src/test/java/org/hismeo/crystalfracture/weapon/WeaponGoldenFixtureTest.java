package org.hismeo.crystalfracture.weapon;

import com.google.gson.JsonParser;
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
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeaponGoldenFixtureTest {
    @Test
    void standardSwordBuildsAnImmutableRegistrySnapshot() throws Exception {
        WeaponPartTypeLoader typeLoader = new WeaponPartTypeLoader();
        List<WeaponPartTypeDefinition> types = new ArrayList<>();
        for (String name : List.of(
                "sword_grip", "golden_sword_guard", "golden_sword_blade")) {
            WeaponPartTypeId id = WeaponPartTypeId.parse("crystal_fracture:" + name);
            try (Reader json = resource("/data/crystal_fracture/crystal_fracture/weapon_part_types/" + name + ".json")) {
                types.add(typeLoader.load(id, json).value());
            }
        }

        WeaponPartLoader partLoader = new WeaponPartLoader();
        List<WeaponPartDefinition> parts = new ArrayList<>();
        for (String name : List.of("oak_grip", "iron_crossguard", "steel_blade")) {
            WeaponPartId id = WeaponPartId.parse("crystal_fracture:" + name);
            try (Reader json = resource("/data/crystal_fracture/crystal_fracture/weapon_parts/" + name + ".json")) {
                parts.add(partLoader.load(id, json).value());
            }
        }

        WeaponSchemaId schemaId = WeaponSchemaId.parse("crystal_fracture:standard_sword");
        WeaponSchemaDefinition schema;
        try (Reader json = resource(
                "/data/crystal_fracture/crystal_fracture/weapon_schemas/standard_sword.json")) {
            schema = new WeaponSchemaLoader().load(schemaId, json).value();
        }

        var result = new WeaponDefinitionRegistry().build(types, parts, List.of(schema));
        assertTrue(result.valid(), () -> "unexpected problems: " + result.problems());
        var snapshot = result.snapshot().orElseThrow();
        assertEquals(3, snapshot.partTypes().size());
        assertEquals(3, snapshot.parts().size());
        assertEquals(List.of("blade", "crossguard", "grip"), schema.slots().keySet().stream()
                .map(WeaponSlotId::value).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.schemas().put(schemaId, schema));
        assertThrows(UnsupportedOperationException.class,
                () -> schema.slots().clear());
    }

    @Test
    void assemblyFixtureContainsOnlyAuthorityIds() throws Exception {
        try (Reader json = resource("/org/hismeo/crystalfracture/weapon/standard_sword_assembly.json")) {
            var root = JsonParser.parseReader(json).getAsJsonObject();
            assertEquals(List.of("parts", "schema"), root.keySet().stream().sorted().toList());
            Map<WeaponSlotId, WeaponPartId> parts = new LinkedHashMap<>();
            root.getAsJsonObject("parts").entrySet().forEach(entry -> parts.put(
                    new WeaponSlotId(entry.getKey()), WeaponPartId.parse(entry.getValue().getAsString())));
            WeaponAssembly assembly = new WeaponAssembly(
                    WeaponSchemaId.parse(root.get("schema").getAsString()), parts);
            assertEquals(WeaponPartId.parse("crystal_fracture:steel_blade"),
                    assembly.parts().get(new WeaponSlotId("blade")));
            assertThrows(UnsupportedOperationException.class, assembly.parts()::clear);
        }
    }

    private Reader resource(String path) {
        var stream = getClass().getResourceAsStream(path);
        assertNotNull(stream, "missing fixture " + path);
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
