package org.hismeo.fractureclient.client.weapon;

import com.kaleblangley.haikalat.core.assets.AssetByteResolver;
import com.kaleblangley.haikalat.core.assets.AssetRef;
import com.kaleblangley.haikalat.core.assets.gltf.GltfAssetLoader;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.internal.compile.WeaponAssemblyCompiler;
import org.hismeo.crystalfracture.weapon.internal.reload.WeaponResourceBatchLoader;
import org.joml.Matrix4fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportedWeaponVisualTest {
    @Test
    void everyUpdatedExportResolvesItsDeclaredConnectsAndMarkers() throws IOException {
        WeaponResourceBatchLoader loader = new WeaponResourceBatchLoader();
        Map<ResourceLocation, String> definitions = new TreeMap<>();
        for (String type : List.of("short_handle", "long_handle", "sword_guard", "sword_blade", "weapon_head")) {
            add(definitions, "weapon_part_types/" + type + ".json");
        }
        for (String part : List.of(
                "bamboo_long_shaft", "bamboo_shaft", "crossguard", "dh_hammer",
                "gaint_sword", "iron_long_shaft", "iron_shaft", "labrys", "rapier",
                "sword", "wild_crossguard", "wood_long_shaft", "wood_shaft")) {
            add(definitions, "weapon_parts/" + part + ".json");
        }
        var loaded = loader.load(definitions);
        assertTrue(loaded.snapshot().isPresent(), () -> loaded.problems().toString());

        GltfAssetLoader gltf = new GltfAssetLoader(new ClasspathAssetResolver());
        loaded.snapshot().orElseThrow().parts().values().forEach(part -> {
            WeaponPartVisualNodes nodes = WeaponPartVisualNodes.from(
                    part,
                    gltf.load(AssetRef.of(part.visualModel().getPath())));
            java.util.stream.Stream.concat(
                            part.connects().values().stream(),
                            part.markers().values().stream())
                    .forEach(node -> {
                        assertTrue(!nodes.ambiguous(node), () -> part.id() + " ambiguous " + node);
                        Matrix4fc transform = nodes.node(node).orElseThrow(
                                () -> new AssertionError(part.id() + " missing " + node));
                        assertTrue(Float.isFinite(transform.determinant()),
                                () -> part.id() + " non-finite " + node);
                    });
        });
    }

    @Test
    void parsesAndAssemblesTheThreeExportedGlbPartsWithoutGpuUpload() throws IOException {
        WeaponResourceBatchLoader loader = new WeaponResourceBatchLoader();
        Map<ResourceLocation, String> definitions = new TreeMap<>();
        add(definitions, "weapon_part_types/short_handle.json");
        add(definitions, "weapon_part_types/sword_guard.json");
        add(definitions, "weapon_part_types/sword.json");
        add(definitions, "weapon_parts/wood_shaft.json");
        add(definitions, "weapon_parts/bamboo_shaft.json");
        add(definitions, "weapon_parts/iron_shaft.json");
        add(definitions, "weapon_parts/crossguard.json");
        add(definitions, "weapon_parts/wild_crossguard.json");
        add(definitions, "weapon_parts/sword.json");
        add(definitions, "weapon_schemas/exported_standard_sword.json");
        var registry = loader.load(definitions).snapshot().orElseThrow();

        WeaponSlotId handle = new WeaponSlotId("handle");
        WeaponSlotId guard = new WeaponSlotId("crossguard");
        WeaponSlotId blade = new WeaponSlotId("blade");
        GltfAssetLoader gltf = new GltfAssetLoader(new ClasspathAssetResolver());
        Map<WeaponPartId, WeaponPartVisualNodes> visualCache = new TreeMap<>();
        org.hismeo.crystalfracture.weapon.api.CompiledWeaponAssembly logical = null;
        WeaponVisualCompileResult assembled = null;
        for (String handleName : new String[]{"wood_shaft", "bamboo_shaft", "iron_shaft"}) {
            for (String guardName : new String[]{"crossguard", "wild_crossguard"}) {
                var candidateLogical = new WeaponAssemblyCompiler().compile(
                        new WeaponAssembly(
                                WeaponSchemaId.parse("crystal_fracture:exported_standard_sword"),
                                Map.of(
                                        handle, WeaponPartId.parse("crystal_fracture:" + handleName),
                                        guard, WeaponPartId.parse("crystal_fracture:" + guardName),
                                        blade, WeaponPartId.parse("crystal_fracture:sword"))),
                        registry).assembly().orElseThrow();
                logical = candidateLogical;
                Map<WeaponPartId, WeaponPartVisualNodes> selectedVisuals = new TreeMap<>();
                candidateLogical.parts().values().forEach(part -> selectedVisuals.put(
                        part.id(),
                        visualCache.computeIfAbsent(part.id(), ignored ->
                                WeaponPartVisualNodes.from(
                                        part,
                                        gltf.load(AssetRef.of(part.visualModel().getPath()))))));
                WeaponVisualCompileResult candidate =
                        new WeaponVisualAssembler().compile(candidateLogical, selectedVisuals);
                assembled = candidate;
                assertTrue(candidate.assembly().isPresent(),
                        () -> handleName + "/" + guardName + ": " + candidate.problems());
            }
        }
        java.util.Objects.requireNonNull(logical);
        java.util.Objects.requireNonNull(assembled);
        var finalLogical = logical;
        for (WeaponSlotId slot : logical.parts().keySet()) {
            Matrix4fc transform = assembled.assembly().orElseThrow().partTransform(slot);
            assertTrue(Float.isFinite(transform.determinant()));
            assertTrue(Math.abs(transform.determinant()) > 1.0E-8F);
        }
        Map<WeaponPartId, WeaponPartVisualNodes> selectedVisuals = new TreeMap<>();
        finalLogical.parts().values().forEach(part -> selectedVisuals.put(
                part.id(), visualCache.get(part.id())));
        Matrix4f mount = WeaponPartVisualCatalog.mountCorrection(
                assembled.assembly().orElseThrow(), selectedVisuals);
        Matrix4fc grip = assembled.assembly().orElseThrow()
                .markerTransform(new org.hismeo.crystalfracture.weapon.api.MarkerName("main_hand_grip"))
                .orElseThrow();
        Vector3f mountedGrip = new Matrix4f(mount).mul(grip)
                .transformPosition(new Vector3f());
        assertTrue(mountedGrip.length() <= 1.0E-5F, () -> mountedGrip.toString());
        assertTrue(Math.abs(mount.determinant3x3() - (1.0F / 4096.0F)) <= 1.0E-7F);
        assertTrue(assembled.assembly().orElseThrow()
                .markerTransform(new org.hismeo.crystalfracture.weapon.api.MarkerName("trail_start"))
                .isPresent());
        assertTrue(assembled.assembly().orElseThrow()
                .markerTransform(new org.hismeo.crystalfracture.weapon.api.MarkerName("trail_end"))
                .isPresent());
    }

    private static void add(Map<ResourceLocation, String> result, String relative)
            throws IOException {
        String classpath = "/data/crystal_fracture/crystal_fracture/" + relative;
        try (var stream = ExportedWeaponVisualTest.class.getResourceAsStream(classpath)) {
            byte[] bytes = java.util.Objects.requireNonNull(stream, classpath).readAllBytes();
            result.put(
                    ResourceLocation.fromNamespaceAndPath(
                            "crystal_fracture", "crystal_fracture/" + relative),
                    new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static final class ClasspathAssetResolver implements AssetByteResolver {
        @Override
        public byte[] readBytes(AssetRef asset, long maxBytes) {
            String path = "/assets/crystal_fracture/" + strip(asset.path());
            try (var stream = ExportedWeaponVisualTest.class.getResourceAsStream(path)) {
                byte[] result = java.util.Objects.requireNonNull(stream, path).readAllBytes();
                if (result.length > maxBytes) {
                    throw new IllegalArgumentException("asset exceeds maxBytes: " + path);
                }
                return BlockbenchGlbCompatibility.sanitize(result).bytes();
            } catch (IOException failure) {
                throw new IllegalArgumentException("Could not read " + path, failure);
            }
        }

        @Override
        public boolean exists(AssetRef asset) {
            String path = "/assets/crystal_fracture/" + strip(asset.path());
            return ExportedWeaponVisualTest.class.getResource(path) != null;
        }

        @Override
        public AssetRef resolveRelative(AssetRef base, String relative) {
            String path = strip(base.path());
            int slash = path.lastIndexOf('/');
            return AssetRef.of((slash < 0 ? "" : path.substring(0, slash + 1)) + relative);
        }

        private static String strip(String path) {
            return path.startsWith("/") ? path.substring(1) : path;
        }
    }
}
