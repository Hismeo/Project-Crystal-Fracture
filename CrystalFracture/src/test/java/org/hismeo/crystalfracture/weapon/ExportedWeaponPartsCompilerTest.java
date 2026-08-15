package org.hismeo.crystalfracture.weapon;

import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectEndpoint;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponMarkerExport;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSlotDefinition;
import org.hismeo.crystalfracture.weapon.internal.compile.WeaponAssemblyCompiler;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartLoader;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExportedWeaponPartsCompilerTest {
    private static final WeaponPartTypeId SHORT_HANDLE = WeaponPartTypeId.parse("crystal_fracture:short_handle");
    private static final WeaponPartTypeId LONG_HANDLE = WeaponPartTypeId.parse("crystal_fracture:long_handle");
    private static final WeaponPartTypeId SWORD_GUARD = WeaponPartTypeId.parse("crystal_fracture:sword_guard");
    private static final WeaponPartTypeId SWORD_BLADE = WeaponPartTypeId.parse("crystal_fracture:sword_blade");
    private static final WeaponPartTypeId WEAPON_HEAD = WeaponPartTypeId.parse("crystal_fracture:weapon_head");
    private static final WeaponSchemaId SCHEMA_ID = WeaponSchemaId.parse("crystal_fracture:exported_standard_sword");

    @Test
    void exportedPartsLoadAndCompileWithoutReadingGlb() throws Exception {
        List<WeaponPartDefinition> parts = loadExportedParts();
        assertEquals(13, parts.size());

        var registryResult = new WeaponDefinitionRegistry().build(
                exportedTypes(), parts, List.of(exportedSwordSchema()));
        assertTrue(registryResult.valid(), () -> "exported data problems: " + registryResult.problems());
        var registry = registryResult.snapshot().orElseThrow();
        WeaponAssemblyCompiler compiler = new WeaponAssemblyCompiler();

        var wood = compiler.compile(assembly("wood_shaft", "crossguard", "sword"), registry);
        assertTrue(wood.valid(), () -> "compile problems: " + wood.problems());
        var compiled = wood.assembly().orElseThrow();
        assertEquals(List.of("handle", "guard"), compiled.parentFirstConnections().stream()
                .map(connection -> connection.parent().slot().value()).toList());
        assertEquals(List.of("guard", "blade"), compiled.parentFirstConnections().stream()
                .map(connection -> connection.child().slot().value()).toList());
        assertEquals("wood_shaft_guard", compiled.parentFirstConnections().get(0).parentNode());
        assertEquals("crossguard_guard", compiled.parentFirstConnections().get(0).childNode());
        assertEquals("crossguard_blade", compiled.parentFirstConnections().get(1).parentNode());
        assertEquals("sword_mount", compiled.parentFirstConnections().get(1).childNode());
        assertEquals(
                List.of("main_hand_grip", "trail_end", "trail_start"),
                compiled.markers().keySet().stream().map(MarkerName::value).toList());
        assertEquals("wood_shaft_hand", compiled.marker(new MarkerName("main_hand_grip"))
                .orElseThrow().nodeName());

        var ironWild = compiler.compile(assembly("iron_shaft", "wild_crossguard", "gaint_sword"), registry)
                .assembly().orElseThrow();
        assertNotEquals(compiled.contentHash(), ironWild.contentHash());
    }

    private static List<WeaponPartDefinition> loadExportedParts() throws Exception {
        List<String> names = List.of(
                "bamboo_long_shaft",
                "bamboo_shaft",
                "crossguard",
                "dh_hammer",
                "gaint_sword",
                "iron_long_shaft",
                "iron_shaft",
                "labrys",
                "rapier",
                "sword",
                "wild_crossguard",
                "wood_long_shaft",
                "wood_shaft"
        );
        WeaponPartLoader loader = new WeaponPartLoader();
        List<WeaponPartDefinition> result = new ArrayList<>();
        for (String name : names) {
            try (Reader json = WeaponTestFixtures.resource(
                    "/org/hismeo/crystalfracture/weapon/exported/parts/" + name + ".json")) {
                WeaponPartId id = WeaponPartId.parse("crystal_fracture:" + name);
                result.add(loader.load(id, json).value());
            }
        }
        return result;
    }

    private static List<WeaponPartTypeDefinition> exportedTypes() {
        return List.of(
                type(SHORT_HANDLE, Set.of("guard"), Set.of("grip")),
                type(LONG_HANDLE, Set.of("head"), Set.of("grip")),
                type(SWORD_GUARD, Set.of("guard", "blade"), Set.of()),
                type(SWORD_BLADE, Set.of("blade"), Set.of("trail_start", "trail_end")),
                type(WEAPON_HEAD, Set.of("head"), Set.of())
        );
    }

    private static WeaponPartTypeDefinition type(
            WeaponPartTypeId id,
            Set<String> connects,
            Set<String> markers
    ) {
        Set<ConnectName> requiredConnects = connects.stream()
                .map(ConnectName::new)
                .collect(java.util.stream.Collectors.toSet());
        Set<MarkerName> requiredMarkers = markers.stream()
                .map(MarkerName::new)
                .collect(java.util.stream.Collectors.toSet());
        return new WeaponPartTypeDefinition(id, requiredConnects, requiredMarkers);
    }

    private static WeaponSchemaDefinition exportedSwordSchema() {
        return new WeaponSchemaDefinition(
                SCHEMA_ID,
                1,
                new WeaponSlotId("handle"),
                Map.of(
                        new WeaponSlotId("handle"), new WeaponSlotDefinition(SHORT_HANDLE),
                        new WeaponSlotId("guard"), new WeaponSlotDefinition(SWORD_GUARD),
                        new WeaponSlotId("blade"), new WeaponSlotDefinition(SWORD_BLADE)
                ),
                List.of(
                        connection("handle", "guard", "guard", "guard"),
                        connection("guard", "blade", "blade", "blade")
                ),
                Map.of(
                        new MarkerName("main_hand_grip"),
                        new WeaponMarkerExport(new WeaponSlotId("handle"), new MarkerName("grip")),
                        new MarkerName("trail_start"),
                        new WeaponMarkerExport(new WeaponSlotId("blade"), new MarkerName("trail_start")),
                        new MarkerName("trail_end"),
                        new WeaponMarkerExport(new WeaponSlotId("blade"), new MarkerName("trail_end"))
                )
        );
    }

    private static WeaponConnectionDefinition connection(
            String parentSlot,
            String parentConnect,
            String childSlot,
            String childConnect
    ) {
        return new WeaponConnectionDefinition(
                new WeaponConnectEndpoint(
                        new WeaponSlotId(parentSlot), new ConnectName(parentConnect)),
                new WeaponConnectEndpoint(
                        new WeaponSlotId(childSlot), new ConnectName(childConnect))
        );
    }

    private static WeaponAssembly assembly(String handle, String guard, String blade) {
        return new WeaponAssembly(
                SCHEMA_ID,
                Map.of(
                        new WeaponSlotId("handle"), WeaponPartId.parse("crystal_fracture:" + handle),
                        new WeaponSlotId("guard"), WeaponPartId.parse("crystal_fracture:" + guard),
                        new WeaponSlotId("blade"), WeaponPartId.parse("crystal_fracture:" + blade)
                )
        );
    }
}
