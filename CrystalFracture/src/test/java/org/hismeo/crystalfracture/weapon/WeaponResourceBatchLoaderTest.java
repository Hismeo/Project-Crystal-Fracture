package org.hismeo.crystalfracture.weapon;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistryManager;
import org.hismeo.crystalfracture.weapon.internal.reload.WeaponResourceBatchLoader;
import org.hismeo.crystalfracture.weapon.internal.reload.WeaponResourceReloadListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.TreeMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponResourceBatchLoaderTest {
    @Test
    void loadsTypeThenPartThenSchemaAndPublishesReadOnlySnapshot() {
        var loaded = new WeaponResourceBatchLoader().load(validResources());
        assertTrue(loaded.valid(), () -> loaded.problems().toString());

        WeaponRegistryManager manager = new WeaponRegistryManager();
        assertEquals(1L, manager.publish(loaded.snapshot().orElseThrow()));
        assertEquals(1L, manager.generation());
        assertEquals(3, manager.partTypes().size());
        assertEquals(3, manager.parts().size());
        assertEquals(1, manager.schemas().size());
        assertThrows(UnsupportedOperationException.class, manager.parts()::clear);

        var assembly = new WeaponAssembly(
                WeaponSchemaId.parse("test:blade"),
                Map.of(
                        new WeaponSlotId("handle"), WeaponPartId.parse("test:handle"),
                        new WeaponSlotId("guard"), WeaponPartId.parse("test:guard"),
                        new WeaponSlotId("blade"), WeaponPartId.parse("test:blade")));
        assertTrue(manager.compile(assembly).isPresent());
    }

    @Test
    void invalidReplacementCannotReplaceLastKnownGoodSnapshot() {
        WeaponResourceBatchLoader loader = new WeaponResourceBatchLoader();
        WeaponRegistryManager manager = new WeaponRegistryManager();
        var accepted = loader.load(validResources());
        manager.publish(accepted.snapshot().orElseThrow());

        Map<ResourceLocation, String> broken = new java.util.TreeMap<>(validResources());
        broken.put(resource("crystal_fracture/weapon_parts/handle.json"),
                part("test:handle", "test:missing_type", "guard", "handle_guard"));
        var rejected = loader.load(broken);
        assertFalse(rejected.valid());
        rejected.snapshot().ifPresent(manager::publish);

        assertEquals(1L, manager.generation());
        assertTrue(manager.part(WeaponPartId.parse("test:handle")).isPresent());
        assertTrue(rejected.problems().stream()
                .anyMatch(problem -> problem.errorCode().equals("unknown_part_type")));
    }

    @Test
    void serverReloadBoundaryDoesNotReferenceClientOrHaikalatClasses() throws IOException {
        assertServerOnly(WeaponResourceReloadListener.class);
        assertServerOnly(WeaponRegistryManager.class);
    }

    @Test
    void completeBundledDataPackBuildsAsOneDedicatedServerSnapshot() throws IOException {
        Map<ResourceLocation, String> resources = new TreeMap<>();
        for (String name : new String[]{
                "golden_sword_blade", "golden_sword_guard", "legacy_sword_blade",
                "long_handle", "short_handle", "sword_blade", "sword_grip",
                "sword_guard", "weapon_head"}) {
            addMain(resources, "weapon_part_types", name);
        }
        for (String name : new String[]{
                "bamboo_long_shaft", "bamboo_shaft", "crossguard", "dh_hammer",
                "gaint_sword", "heavy_sword", "iron_crossguard", "iron_long_shaft",
                "iron_shaft", "labrys", "oak_grip", "rapier", "steel_blade",
                "sword", "wild_crossguard", "wood_long_shaft", "wood_shaft"}) {
            addMain(resources, "weapon_parts", name);
        }
        addMain(resources, "weapon_schemas", "exported_standard_sword");
        addMain(resources, "weapon_schemas", "standard_sword");

        var loaded = new WeaponResourceBatchLoader().load(resources);
        assertTrue(loaded.valid(), () -> loaded.problems().toString());
        var snapshot = loaded.snapshot().orElseThrow();
        assertEquals(9, snapshot.partTypes().size());
        assertEquals(17, snapshot.parts().size());
        assertEquals(2, snapshot.schemas().size());
    }

    private static Map<ResourceLocation, String> validResources() {
        return Map.of(
                resource("crystal_fracture/weapon_part_types/handle.json"),
                type("guard"),
                resource("crystal_fracture/weapon_part_types/guard.json"),
                type("handle", "blade"),
                resource("crystal_fracture/weapon_part_types/blade.json"),
                type("guard"),
                resource("crystal_fracture/weapon_parts/handle.json"),
                part("test:handle", "test:handle", "guard", "handle_guard"),
                resource("crystal_fracture/weapon_parts/guard.json"),
                """
                {"id":"test:guard","type":"test:guard","visual":{"model":"test:guard.glb"},
                 "connects":{"handle":"guard_handle","blade":"guard_blade"},"markers":{}}
                """,
                resource("crystal_fracture/weapon_parts/blade.json"),
                part("test:blade", "test:blade", "guard", "blade_guard"),
                resource("crystal_fracture/weapon_schemas/blade.json"),
                """
                {"schema_version":1,"root":"handle",
                 "slots":{"handle":{"part_type":"test:handle"},
                          "guard":{"part_type":"test:guard"},
                          "blade":{"part_type":"test:blade"}},
                 "connections":[
                   {"parent":{"slot":"handle","connect":"guard"},
                    "child":{"slot":"guard","connect":"handle"}},
                   {"parent":{"slot":"guard","connect":"blade"},
                    "child":{"slot":"blade","connect":"guard"}}],
                 "markers":{}}
                """);
    }

    private static String type(String... connects) {
        return "{\"required_connects\":[\"" + String.join("\",\"", connects)
                + "\"],\"required_markers\":[]}";
    }

    private static String part(String id, String type, String connect, String node) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type
                + "\",\"visual\":{\"model\":\"test:model.glb\"},\"connects\":{\""
                + connect + "\":\"" + node + "\"},\"markers\":{}}";
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static void addMain(
            Map<ResourceLocation, String> result,
            String directory,
            String name
    ) throws IOException {
        String path = "/data/crystal_fracture/crystal_fracture/"
                + directory + "/" + name + ".json";
        try (InputStream stream = WeaponResourceBatchLoaderTest.class.getResourceAsStream(path)) {
            byte[] bytes = java.util.Objects.requireNonNull(stream, path).readAllBytes();
            result.put(ResourceLocation.fromNamespaceAndPath(
                            "crystal_fracture",
                            "crystal_fracture/" + directory + "/" + name + ".json"),
                    new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static void assertServerOnly(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        byte[] bytecode;
        try (var stream = type.getResourceAsStream(resource)) {
            bytecode = java.util.Objects.requireNonNull(stream, resource).readAllBytes();
        }
        String constants = new String(bytecode, StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("net/minecraft/client"));
        assertFalse(constants.contains("com/kaleblangley/haikalat"));
        assertFalse(constants.contains("org/hismeo/fractureclient"));
    }
}
