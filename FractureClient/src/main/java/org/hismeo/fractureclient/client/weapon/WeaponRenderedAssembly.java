package org.hismeo.fractureclient.client.weapon;

import com.kaleblangley.haikalat.core.material.MaterialInstance;
import com.kaleblangley.haikalat.subsystems.render3d.MeshRenderer;
import com.kaleblangley.haikalat.subsystems.render3d.SceneObject;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfSceneInstance;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.hismeo.crystalfracture.weapon.api.MarkerName;

import java.util.ArrayList;
import java.util.List;

/** Per-player ownership boundary for every Part scene instance in one weapon. */
final class WeaponRenderedAssembly implements AutoCloseable {
    private final WeaponPartVisualCatalog catalog;
    private final List<GltfSceneInstance> instances;
    private final List<MeshRenderer> renderers;
    private final Matrix4f socketWorld = new Matrix4f();
    private boolean socketAvailable;
    private boolean closed;

    WeaponRenderedAssembly(WeaponPartVisualCatalog catalog) {
        this.catalog = catalog;
        List<GltfSceneInstance> created = new ArrayList<>();
        List<MeshRenderer> createdRenderers = new ArrayList<>();
        try {
            for (var slot : catalog.visualAssembly().logical().parts().keySet()) {
                GltfSceneInstance instance = catalog.instantiate(slot);
                created.add(instance);
                instance.objects().stream()
                        .map(object -> renderer(catalog, object))
                        .forEach(createdRenderers::add);
            }
            instances = List.copyOf(created);
            renderers = List.copyOf(createdRenderers);
        } catch (RuntimeException failure) {
            created.reversed().forEach(GltfSceneInstance::close);
            throw failure;
        }
    }

    void updateSocket(Matrix4fc transform) {
        socketWorld.set(transform);
        socketAvailable = true;
    }

    void socketUnavailable() {
        socketAvailable = false;
    }

    boolean ready() {
        return !closed && socketAvailable;
    }

    List<MeshRenderer> renderers() {
        return ready() ? renderers : List.of();
    }

    boolean markerWorld(MarkerName marker, Matrix4f destination) {
        if (!ready()) {
            return false;
        }
        var local = catalog.visualAssembly().markerTransform(marker);
        if (local.isEmpty()) {
            return false;
        }
        destination.set(socketWorld).mul(local.orElseThrow());
        return true;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        socketAvailable = false;
        instances.reversed().forEach(GltfSceneInstance::close);
    }

    private MeshRenderer renderer(WeaponPartVisualCatalog catalog, SceneObject object) {
        MaterialInstance material = catalog.embeddedMaterial(object.material()).createInstance();
        return new MeshRenderer(
                object.mesh(),
                material,
                com.kaleblangley.haikalat.subsystems.render3d.Transform.identity(),
                (destination, frameIndex) -> {
                    object.computeModel(destination, frameIndex);
                    destination.mulLocal(socketWorld);
                },
                false,
                object.drawBinding());
    }
}
