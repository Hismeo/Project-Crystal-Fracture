package org.hismeo.haikalathost.client.material;

import org.hismeo.haikalathost.client.scene.MaterialId;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Render-thread-owned, deduplicated material table for one resource generation. */
public final class MaterialRegistry {
    private final Map<MaterialKey, MaterialId> identifiers = new HashMap<>();
    private final List<MaterialKey> materials = new ArrayList<>();

    public MaterialId resolve(MaterialKey key) {
        Objects.requireNonNull(key, "key");
        MaterialId existing = identifiers.get(key);
        if (existing != null) return existing;
        MaterialId created = new MaterialId(materials.size());
        materials.add(key);
        identifiers.put(key, created);
        return created;
    }

    public MaterialKey get(MaterialId id) {
        Objects.requireNonNull(id, "id");
        if (id.value() >= materials.size()) {
            throw new IllegalArgumentException("unknown material id: " + id);
        }
        return materials.get(id.value());
    }


    public MaterialKey get(int identifier) {
        if (identifier < 0 || identifier >= materials.size()) {
            throw new IllegalArgumentException("unknown material id: " + identifier);
        }
        return materials.get(identifier);
    }
    public void writeGpuTable(ByteBuffer target) {
        Objects.requireNonNull(target, "target");
        int required = Math.multiplyExact(materials.size(), GpuMaterial.BYTES);
        if (target.remaining() < required) {
            throw new IllegalArgumentException(
                    "material table target requires " + required + " bytes, found " + target.remaining());
        }
        for (MaterialKey material : materials) material.gpuMaterial().writeTo(target);
    }

    public int size() {
        return materials.size();
    }

    public void clear() {
        identifiers.clear();
        materials.clear();
    }
}
