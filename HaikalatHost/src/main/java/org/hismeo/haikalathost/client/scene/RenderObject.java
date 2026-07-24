package org.hismeo.haikalathost.client.scene;

import java.util.Objects;

public record RenderObject(
        MeshId mesh,
        MaterialId material,
        TransformId transform,
        Bounds bounds,
        int flags
) {
    public RenderObject {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(bounds, "bounds");
    }
}
