package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * GPU-facing result of one local directional shadow-map pass.
 *
 * <p>The texture contains standard light-space depth in the {@code 0..1} range. The lighting
 * post-pass reconstructs a visible world pixel, projects it with {@link #lightViewProjection()},
 * and compares that depth against this texture.</p>
 */
record OctopathDirectionalShadowSnapshot(
        int depthTextureId,
        Matrix4f lightViewProjection,
        Vector3f lightDirection,
        int resolution,
        boolean available
) {
    OctopathDirectionalShadowSnapshot {
        lightViewProjection = new Matrix4f(Objects.requireNonNull(lightViewProjection, "lightViewProjection"));
        lightDirection = new Vector3f(Objects.requireNonNull(lightDirection, "lightDirection"));
        if (available && (depthTextureId <= 0 || resolution <= 0)) {
            throw new IllegalArgumentException("Available directional shadow map needs a depth texture and resolution");
        }
    }

    @Override
    public Matrix4f lightViewProjection() {
        return new Matrix4f(lightViewProjection);
    }

    @Override
    public Vector3f lightDirection() {
        return new Vector3f(lightDirection);
    }

    static OctopathDirectionalShadowSnapshot unavailable() {
        return new OctopathDirectionalShadowSnapshot(
                0,
                new Matrix4f(),
                new Vector3f(0.0F, -1.0F, 0.0F),
                0,
                false);
    }
}
