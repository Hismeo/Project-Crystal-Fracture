package org.hismeo.crystalfracture.weapon;

import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartLoader;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartTypeLoader;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponSchemaLoader;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionLoadException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class WeaponLoaderContractTest {
    @Test
    void rejectsUnsupportedSchemaVersionAtExactPath() {
        String json = """
                {
                  "schema_version": 2,
                  "root": "grip",
                  "slots": {},
                  "connections": [],
                  "markers": {}
                }
                """;
        assertProblem(
                assertThrows(WeaponDefinitionLoadException.class,
                        () -> new WeaponSchemaLoader().load(
                                WeaponSchemaId.parse("crystal_fracture:test"), json)),
                "unsupported_schema_version", "$.schema_version");
    }

    @Test
    void rejectsIllegalSlotNameAtExactPath() {
        String json = """
                {
                  "schema_version": 1,
                  "root": "Bad Slot",
                  "slots": {},
                  "connections": [],
                  "markers": {}
                }
                """;
        assertProblem(
                assertThrows(WeaponDefinitionLoadException.class,
                        () -> new WeaponSchemaLoader().load(
                                WeaponSchemaId.parse("crystal_fracture:test"), json)),
                "invalid_name", "$.root");
    }

    @Test
    void rejectsUnknownFieldsInsteadOfSilentlyGuessing() {
        String json = """
                {
                  "required_connects": [],
                  "required_markers": [],
                  "inherits": "crystal_fracture:base"
                }
                """;
        assertProblem(
                assertThrows(WeaponDefinitionLoadException.class,
                        () -> new WeaponPartTypeLoader().load(
                                WeaponPartTypeId.parse("crystal_fracture:test"), json)),
                "unknown_field", "$.inherits");
    }

    @Test
    void rejectsInvalidVisualResourceLocationAtExactPath() {
        String json = """
                {
                  "id": "crystal_fracture:test",
                  "type": "crystal_fracture:test_type",
                  "visual": {"model": "Bad Model"},
                  "connects": {},
                  "markers": {}
                }
                """;
        assertProblem(
                assertThrows(WeaponDefinitionLoadException.class,
                        () -> new WeaponPartLoader().load(
                                WeaponPartId.parse("crystal_fracture:test"), json)),
                "invalid_resource_location", "$.visual.model");
    }

    @Test
    void rejectsDuplicateObjectKeysInsteadOfKeepingTheLastValue() {
        String json = """
                {
                  "required_connects": ["first"],
                  "required_connects": ["second"],
                  "required_markers": []
                }
                """;
        assertProblem(
                assertThrows(WeaponDefinitionLoadException.class,
                        () -> new WeaponPartTypeLoader().load(
                                WeaponPartTypeId.parse("crystal_fracture:test"), json)),
                "duplicate_field", "$.required_connects");
    }

    private static void assertProblem(
            WeaponDefinitionLoadException exception,
            String code,
            String path
    ) {
        assertEquals(1, exception.problems().size());
        assertEquals(code, exception.problems().getFirst().errorCode());
        assertEquals(path, exception.problems().getFirst().jsonPath());
    }
}
