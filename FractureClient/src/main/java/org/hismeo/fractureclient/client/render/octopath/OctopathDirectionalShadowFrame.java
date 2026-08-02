package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

/**
 * Immutable CPU description of a bounded proxy directional-shadow pass.
 *
 * <p>All coordinates are world-space. {@link #lightDirection()} points in the direction that
 * incoming light travels, from the light toward the scene. The light-space matrices use the
 * conventional OpenGL clip range and are deliberately independent of the main camera's current
 * projection; this lets a renderer own a small depth texture without taking over Minecraft's
 * world render.</p>
 */
record OctopathDirectionalShadowFrame(
        Vector3f worldCenter,
        Vector3f lightDirection,
        Matrix4f lightView,
        Matrix4f lightProjection,
        Matrix4f lightViewProjection,
        float worldExtent,
        int terrainCellSize,
        List<OctopathDirectionalShadowCaster> terrainCasters,
        List<OctopathDirectionalShadowCaster> entityCasters
) {
    OctopathDirectionalShadowFrame {
        worldCenter = new Vector3f(Objects.requireNonNull(worldCenter, "worldCenter"));
        lightDirection = new Vector3f(Objects.requireNonNull(lightDirection, "lightDirection"));
        lightView = new Matrix4f(Objects.requireNonNull(lightView, "lightView"));
        lightProjection = new Matrix4f(Objects.requireNonNull(lightProjection, "lightProjection"));
        lightViewProjection = new Matrix4f(Objects.requireNonNull(lightViewProjection, "lightViewProjection"));
        terrainCasters = List.copyOf(Objects.requireNonNull(terrainCasters, "terrainCasters"));
        entityCasters = List.copyOf(Objects.requireNonNull(entityCasters, "entityCasters"));
        if (!isFinite(worldCenter)
                || !isFinite(lightDirection)
                || lightDirection.lengthSquared() < 0.00001F
                || !Float.isFinite(worldExtent)
                || worldExtent <= 0.0F
                || terrainCellSize < 1) {
            throw new IllegalArgumentException("Invalid directional shadow frame");
        }
        lightDirection.normalize();
    }

    @Override
    public Vector3f worldCenter() {
        return new Vector3f(worldCenter);
    }

    @Override
    public Vector3f lightDirection() {
        return new Vector3f(lightDirection);
    }

    @Override
    public Matrix4f lightView() {
        return new Matrix4f(lightView);
    }

    @Override
    public Matrix4f lightProjection() {
        return new Matrix4f(lightProjection);
    }

    @Override
    public Matrix4f lightViewProjection() {
        return new Matrix4f(lightViewProjection);
    }

    private static boolean isFinite(Vector3f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }
}
