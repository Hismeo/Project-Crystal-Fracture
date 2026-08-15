package org.hismeo.crystalfracture.weapon;

import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectEndpoint;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSlotDefinition;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartLoader;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionProblem;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeaponDefinitionValidatorTest {
    private static final WeaponPartTypeId TYPE_ID = WeaponPartTypeId.parse("crystal_fracture:node");
    private static final WeaponPartTypeDefinition TYPE = new WeaponPartTypeDefinition(
            TYPE_ID, Set.of(new ConnectName("in"), new ConnectName("out")), Set.of(new MarkerName("tip")));
    private static final Map<WeaponPartTypeId, WeaponPartTypeDefinition> TYPES = Map.of(TYPE_ID, TYPE);

    @Test
    void singleSlotRootIsValid() {
        WeaponSchemaDefinition schema = schema(
                "root", List.of(), Map.of("root", TYPE_ID));
        assertTrue(new WeaponDefinitionValidator().validate(schema, TYPES).isEmpty());
    }

    @Test
    void reportsDisconnectedSlot() {
        WeaponSchemaDefinition schema = schema(
                "root", List.of(), Map.of("root", TYPE_ID, "loose", TYPE_ID));
        assertCodes(schema, "disconnected_slot");
    }

    @Test
    void reportsRootParentCycleAndDuplicateEndpointDeterministically() {
        WeaponSchemaDefinition schema = schema(
                "root",
                List.of(
                        connection("root", "out", "child", "in"),
                        connection("child", "out", "root", "in"),
                        connection("root", "out", "child", "out")
                ),
                Map.of("root", TYPE_ID, "child", TYPE_ID));
        List<String> codes = new WeaponDefinitionValidator().validate(schema, TYPES).stream()
                .map(WeaponDefinitionProblem::errorCode).toList();
        assertTrue(codes.contains("root_has_parent"));
        assertTrue(codes.contains("cyclic_topology"));
        assertTrue(codes.contains("duplicate_endpoint"));
        assertTrue(codes.contains("slot_has_multiple_parents"));
    }

    @Test
    void rejectsMissingPartContractAndDuplicateLocatorBinding() {
        String json = """
                {
                  "id": "crystal_fracture:bad_part",
                  "type": "crystal_fracture:node",
                  "visual": {"model": "crystal_fracture:weapon/parts/bad.glb"},
                  "connects": {"in": "same", "out": "other"},
                  "markers": {"extra": "same"}
                }
                """;
        WeaponPartDefinition part = new WeaponPartLoader().load(
                WeaponPartId.parse("crystal_fracture:bad_part"), json).value();
        List<String> codes = new WeaponDefinitionValidator().validate(part, TYPES).stream()
                .map(WeaponDefinitionProblem::errorCode).toList();
        assertTrue(codes.contains("missing_required_marker"));
        assertTrue(codes.contains("duplicate_locator_binding"));
    }

    private static WeaponSchemaDefinition schema(
            String root,
            List<WeaponConnectionDefinition> connections,
            Map<String, WeaponPartTypeId> slots
    ) {
        Map<WeaponSlotId, WeaponSlotDefinition> definitions = slots.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> new WeaponSlotId(entry.getKey()),
                        entry -> new WeaponSlotDefinition(entry.getValue())));
        return new WeaponSchemaDefinition(
                WeaponSchemaId.parse("crystal_fracture:test"),
                1,
                new WeaponSlotId(root),
                definitions,
                connections,
                Map.of());
    }

    private static WeaponConnectionDefinition connection(
            String parentSlot,
            String parentConnect,
            String childSlot,
            String childConnect
    ) {
        return new WeaponConnectionDefinition(
                new WeaponConnectEndpoint(new WeaponSlotId(parentSlot), new ConnectName(parentConnect)),
                new WeaponConnectEndpoint(new WeaponSlotId(childSlot), new ConnectName(childConnect)));
    }

    private static void assertCodes(WeaponSchemaDefinition schema, String... expected) {
        List<String> codes = new WeaponDefinitionValidator().validate(schema, TYPES).stream()
                .map(WeaponDefinitionProblem::errorCode).toList();
        assertEquals(List.of(expected), codes);
    }
}
