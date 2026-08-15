package org.hismeo.fractureclient.client.weapon;

import com.kaleblangley.haikalat.core.material.Material;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfSceneInstance;
import org.hismeo.crystalfracture.weapon.api.CompiledWeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One atomically accepted GLB generation for a compiled weapon selection. */
public final class WeaponPartVisualCatalog implements AutoCloseable {
    private static final float BLOCKBENCH_PIXELS_TO_WORLD = 1.0F / 16.0F;

    private final WeaponPartVisualLibrary library;
    private final WeaponVisualAssembly visualAssembly;
    private final Matrix4f socketToWeaponRoot;
    private boolean closed;

    private WeaponPartVisualCatalog(
            WeaponPartVisualLibrary library,
            WeaponVisualAssembly visualAssembly,
            Matrix4f socketToWeaponRoot
    ) {
        this.library = library;
        this.visualAssembly = visualAssembly;
        this.socketToWeaponRoot = new Matrix4f(socketToWeaponRoot);
    }

    public static WeaponPartVisualCatalog load(
            CompiledWeaponAssembly assembly,
            WeaponPartVisualLibrary library
    ) {
        Objects.requireNonNull(assembly, "assembly");
        Objects.requireNonNull(library, "library");

        Map<WeaponPartId, WeaponPartVisualNodes> visualNodes = new TreeMap<>();
        assembly.parts().values().stream()
                .distinct()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .forEach(part -> visualNodes.put(part.id(), library.nodes(part)));

        WeaponVisualCompileResult visual = new WeaponVisualAssembler().compile(
                assembly, visualNodes);
        if (visual.assembly().isEmpty()) {
            throw new WeaponVisualCompilationException(visual.problems());
        }
        Matrix4f socketToWeaponRoot = mountCorrection(
                visual.assembly().orElseThrow(), visualNodes);
        return new WeaponPartVisualCatalog(
                library,
                visual.assembly().orElseThrow(),
                socketToWeaponRoot);
    }

    public WeaponVisualAssembly visualAssembly() {
        ensureOpen();
        return visualAssembly;
    }

    public long resourceGeneration() {
        return library.resourceGeneration();
    }

    public Matrix4fc weaponRootAtSocket(Matrix4fc socketWorld, Matrix4f destination) {
        ensureOpen();
        return destination.set(socketWorld).mul(socketToWeaponRoot);
    }

    public GltfSceneInstance instantiate(WeaponSlotId slot) {
        ensureOpen();
        WeaponPartDefinition part = visualAssembly.logical().parts().get(slot);
        if (part == null) {
            throw new IllegalArgumentException("Unknown weapon slot " + slot);
        }
        return library.instantiate(part, visualAssembly.partTransform(slot));
    }

    public Material embeddedMaterial(Material source) {
        ensureOpen();
        return library.embeddedMaterial(source);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
    }

    /**
     * Phase 4 prefers the formal marker, then the exported root `grip` semantic Connect.
     * Phase 5 makes main_hand_grip authoritative over the networked Assembly.
     */
    static Matrix4f mountCorrection(
            WeaponVisualAssembly visual,
            Map<WeaponPartId, WeaponPartVisualNodes> nodes
    ) {
        Matrix4fc gripInWeapon = visual.markerTransform(new MarkerName("main_hand_grip"))
                .orElseGet(() -> {
                    var rootSlot = visual.logical().root();
                    var rootPart = visual.logical().parts().get(rootSlot);
                    String nodeName = rootPart.connects().get(new ConnectName("grip"));
                    if (nodeName == null) {
                        return new Matrix4f();
                    }
                    Matrix4fc local = nodes.get(rootPart.id()).node(nodeName).orElseThrow();
                    return new Matrix4f(visual.partTransform(rootSlot)).mul(local);
                });
        float determinant = gripInWeapon.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) <= 1.0E-8F) {
            var rootSlot = visual.logical().root();
            var rootPart = visual.logical().parts().get(rootSlot);
            throw new WeaponVisualCompilationException(java.util.List.of(
                    new WeaponVisualProblem(
                            "non_invertible_mount_transform",
                            rootSlot,
                            rootPart.id(),
                            "main_hand_grip/grip",
                            "weapon hand mount must be finite and invertible")));
        }
        return new Matrix4f()
                .scale(BLOCKBENCH_PIXELS_TO_WORLD)
                .mul(new Matrix4f(gripInWeapon).invert());
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("weapon visual catalog is closed");
        }
    }

}
