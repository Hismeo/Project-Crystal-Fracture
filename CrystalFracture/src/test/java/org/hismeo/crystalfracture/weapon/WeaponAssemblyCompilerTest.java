package org.hismeo.crystalfracture.weapon;

import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectEndpoint;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSlotDefinition;
import org.hismeo.crystalfracture.weapon.internal.compile.WeaponAssemblyCompiler;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartLoader;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeaponAssemblyCompilerTest {
    @Test
    void compilesStandardSwordToAReadOnlyLogicalAssembly() throws Exception {
        var registry = WeaponTestFixtures.standardSwordRegistry();
        var result = new WeaponAssemblyCompiler().compile(
                WeaponTestFixtures.standardSwordAssembly(), registry);

        assertTrue(result.valid(), () -> "unexpected compile problems: " + result.problems());
        var compiled = result.assembly().orElseThrow();
        assertEquals("grip", compiled.root().value());
        assertEquals(List.of("grip", "crossguard", "blade"), compiled.parts().keySet().stream()
                .map(WeaponSlotId::value).toList());
        assertEquals(List.of("grip", "crossguard"), compiled.parentFirstConnections().stream()
                .map(connection -> connection.parent().slot().value()).toList());
        assertEquals(List.of("crossguard", "blade"), compiled.parentFirstConnections().stream()
                .map(connection -> connection.child().slot().value()).toList());
        assertEquals("guard_connect", compiled.parentFirstConnections().getFirst().parentNode());
        assertEquals("grip_connect", compiled.parentFirstConnections().getFirst().childNode());
        var bladeTip = compiled.marker(new MarkerName("blade_tip")).orElseThrow();
        assertEquals("blade", bladeTip.slot().value());
        assertEquals(WeaponPartId.parse("crystal_fracture:steel_blade"), bladeTip.part());
        assertEquals("blade_tip", bladeTip.nodeName());
        assertTrue(compiled.contentHash().matches("[0-9a-f]{64}"));
        assertThrows(UnsupportedOperationException.class, compiled.parts()::clear);
        assertThrows(UnsupportedOperationException.class, compiled.markers()::clear);
        assertThrows(UnsupportedOperationException.class, compiled.parentFirstConnections()::clear);
    }

    @Test
    void schemaJsonConnectionOrderDoesNotChangeCompileOrderOrHash() throws Exception {
        var originalRegistry = WeaponTestFixtures.standardSwordRegistry();
        WeaponSchemaDefinition original = originalRegistry.schemas().values().iterator().next();
        var reversedConnections = new ArrayList<>(original.connections());
        java.util.Collections.reverse(reversedConnections);
        WeaponSchemaDefinition reversed = new WeaponSchemaDefinition(
                original.id(), original.schemaVersion(), original.root(), original.slots(),
                reversedConnections, original.markers());
        var registryResult = new WeaponDefinitionRegistry().build(
                originalRegistry.partTypes().values(), originalRegistry.parts().values(), List.of(reversed));
        assertTrue(registryResult.valid(), () -> "unexpected registry problems: " + registryResult.problems());

        WeaponAssemblyCompiler compiler = new WeaponAssemblyCompiler();
        var first = compiler.compile(
                WeaponTestFixtures.standardSwordAssembly(), originalRegistry).assembly().orElseThrow();
        var second = compiler.compile(
                WeaponTestFixtures.standardSwordAssembly(), registryResult.snapshot().orElseThrow())
                .assembly().orElseThrow();
        assertEquals(first.parentFirstConnections(), second.parentFirstConnections());
        assertEquals(first.contentHash(), second.contentHash());
    }

    @Test
    void rejectsMissingUnexpectedUnknownAndMismatchedPartsWithoutPartialOutput() throws Exception {
        var registry = WeaponTestFixtures.standardSwordRegistry();
        Map<WeaponSlotId, WeaponPartId> parts = new HashMap<>(
                WeaponTestFixtures.standardSwordAssembly().parts());
        parts.remove(new WeaponSlotId("blade"));
        parts.put(new WeaponSlotId("extra"), WeaponPartId.parse("crystal_fracture:missing"));
        var shapeResult = new WeaponAssemblyCompiler().compile(
                new WeaponAssembly(WeaponTestFixtures.standardSwordAssembly().schema(), parts), registry);
        assertFalse(shapeResult.valid());
        assertTrue(shapeResult.problems().stream()
                .anyMatch(problem -> problem.errorCode().equals("missing_assembly_slot")));
        assertTrue(shapeResult.problems().stream()
                .anyMatch(problem -> problem.errorCode().equals("unexpected_assembly_slot")));

        parts = new HashMap<>(WeaponTestFixtures.standardSwordAssembly().parts());
        parts.put(new WeaponSlotId("blade"), WeaponPartId.parse("crystal_fracture:iron_crossguard"));
        var typeResult = new WeaponAssemblyCompiler().compile(
                new WeaponAssembly(WeaponTestFixtures.standardSwordAssembly().schema(), parts), registry);
        assertFalse(typeResult.valid());
        assertTrue(typeResult.problems().stream()
                .anyMatch(problem -> problem.errorCode().equals("part_type_mismatch")));

        parts.put(new WeaponSlotId("blade"), WeaponPartId.parse("crystal_fracture:not_registered"));
        var unknownResult = new WeaponAssemblyCompiler().compile(
                new WeaponAssembly(WeaponTestFixtures.standardSwordAssembly().schema(), parts), registry);
        assertFalse(unknownResult.valid());
        assertTrue(unknownResult.problems().stream()
                .anyMatch(problem -> problem.errorCode().equals("unknown_part")));
    }

    @Test
    void changingASelectedPartChangesContentHash() throws Exception {
        var originalRegistry = WeaponTestFixtures.standardSwordRegistry();
        String alternateJson = """
                {
                  "id": "crystal_fracture:tempered_blade",
                  "type": "crystal_fracture:golden_sword_blade",
                  "visual": {"model": "crystal_fracture:weapon/parts/tempered_blade.glb"},
                  "connects": {"guard": "tempered_guard_connect"},
                  "markers": {
                    "tip": "tempered_tip",
                    "base": "tempered_base",
                    "trail_start": "tempered_trail_start",
                    "trail_end": "tempered_trail_end"
                  }
                }
                """;
        var alternate = new WeaponPartLoader().load(
                WeaponPartId.parse("crystal_fracture:tempered_blade"), alternateJson).value();
        var availableParts = new ArrayList<>(originalRegistry.parts().values());
        availableParts.add(alternate);
        var registryResult = new WeaponDefinitionRegistry().build(
                originalRegistry.partTypes().values(),
                availableParts,
                originalRegistry.schemas().values());
        assertTrue(registryResult.valid(), () -> "unexpected registry problems: " + registryResult.problems());
        var registry = registryResult.snapshot().orElseThrow();
        WeaponAssemblyCompiler compiler = new WeaponAssemblyCompiler();
        var original = compiler.compile(
                WeaponTestFixtures.standardSwordAssembly(), registry).assembly().orElseThrow();

        Map<WeaponSlotId, WeaponPartId> parts = new HashMap<>(
                WeaponTestFixtures.standardSwordAssembly().parts());
        parts.put(new WeaponSlotId("blade"), WeaponPartId.parse("crystal_fracture:tempered_blade"));
        var changed = compiler.compile(
                new WeaponAssembly(WeaponTestFixtures.standardSwordAssembly().schema(), parts), registry)
                .assembly().orElseThrow();
        assertNotEquals(original.contentHash(), changed.contentHash());
    }

    @Test
    void samePartIdStillOccupiesTwoIndependentSlots() {
        WeaponPartTypeId typeId = WeaponPartTypeId.parse("crystal_fracture:link");
        var type = new WeaponPartTypeDefinition(
                typeId, Set.of(new ConnectName("in"), new ConnectName("out")), Set.of());
        String partJson = """
                {
                  "id": "crystal_fracture:shared_link",
                  "type": "crystal_fracture:link",
                  "visual": {"model": "crystal_fracture:weapon/parts/shared_link.glb"},
                  "connects": {"in": "link_in", "out": "link_out"},
                  "markers": {}
                }
                """;
        WeaponPartId partId = WeaponPartId.parse("crystal_fracture:shared_link");
        var part = new WeaponPartLoader().load(partId, partJson).value();
        WeaponSchemaId schemaId = WeaponSchemaId.parse("crystal_fracture:double_link");
        var schema = new WeaponSchemaDefinition(
                schemaId,
                1,
                new WeaponSlotId("first"),
                Map.of(
                        new WeaponSlotId("first"), new WeaponSlotDefinition(typeId),
                        new WeaponSlotId("second"), new WeaponSlotDefinition(typeId)),
                List.of(new WeaponConnectionDefinition(
                        new WeaponConnectEndpoint(new WeaponSlotId("first"), new ConnectName("out")),
                        new WeaponConnectEndpoint(new WeaponSlotId("second"), new ConnectName("in")))),
                Map.of());
        var registryResult = new WeaponDefinitionRegistry().build(
                List.of(type), List.of(part), List.of(schema));
        assertTrue(registryResult.valid(), () -> "unexpected registry problems: " + registryResult.problems());
        var assembly = new WeaponAssembly(
                schemaId,
                Map.of(new WeaponSlotId("first"), partId, new WeaponSlotId("second"), partId));

        var compiled = new WeaponAssemblyCompiler().compile(
                assembly, registryResult.snapshot().orElseThrow()).assembly().orElseThrow();
        assertEquals(List.of("first", "second"), compiled.parts().keySet().stream()
                .map(WeaponSlotId::value).toList());
        assertEquals(partId, compiled.parts().get(new WeaponSlotId("first")).id());
        assertEquals(partId, compiled.parts().get(new WeaponSlotId("second")).id());
    }
}
