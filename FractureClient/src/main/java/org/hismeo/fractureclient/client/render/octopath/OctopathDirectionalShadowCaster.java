package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * One deliberately simple proxy caster for the directional shadow atlas.
 *
 * <p>The renderer draws a unit cube ({@code -0.5..0.5}) with {@link #modelMatrix()}. Terrain
 * columns and living entities intentionally share this representation: it keeps the first
 * shadow-map pass bounded and makes no attempt to recreate Minecraft's arbitrary block models
 * or animated entity meshes.</p>
 */
record OctopathDirectionalShadowCaster(
        Matrix4f modelMatrix,
        Vector3f center,
        Vector3f halfExtents
) {
    OctopathDirectionalShadowCaster {
        modelMatrix = new Matrix4f(Objects.requireNonNull(modelMatrix, "modelMatrix"));
        center = new Vector3f(Objects.requireNonNull(center, "center"));
        halfExtents = new Vector3f(Objects.requireNonNull(halfExtents, "halfExtents"));
        if (!isFinite(center) || !isFinite(halfExtents)
                || halfExtents.x <= 0.0F
                || halfExtents.y <= 0.0F
                || halfExtents.z <= 0.0F) {
            throw new IllegalArgumentException("Directional shadow caster must have finite positive extents");
        }
    }

    @Override
    public Matrix4f modelMatrix() {
        return new Matrix4f(modelMatrix);
    }

    @Override
    public Vector3f center() {
        return new Vector3f(center);
    }

    @Override
    public Vector3f halfExtents() {
        return new Vector3f(halfExtents);
    }

    /**
     * Borrowed immutable transform for the shadow-map instance upload. Package-private on
     * purpose: callers outside the bounded render path still receive defensive copies.
     */
    Matrix4f modelMatrixSnapshot() {
        return modelMatrix;
    }

    static OctopathDirectionalShadowCaster cube(
            float centerX,
            float centerY,
            float centerZ,
            float width,
            float height,
            float depth
    ) {
        float safeWidth = Math.max(0.001F, width);
        float safeHeight = Math.max(0.001F, height);
        float safeDepth = Math.max(0.001F, depth);
        Vector3f center = new Vector3f(centerX, centerY, centerZ);
        Vector3f halfExtents = new Vector3f(
                safeWidth * 0.5F,
                safeHeight * 0.5F,
                safeDepth * 0.5F);
        Matrix4f model = new Matrix4f()
                .translation(centerX, centerY, centerZ)
                .scale(safeWidth, safeHeight, safeDepth);
        return new OctopathDirectionalShadowCaster(model, center, halfExtents);
    }

    private static boolean isFinite(Vector3f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }
}
