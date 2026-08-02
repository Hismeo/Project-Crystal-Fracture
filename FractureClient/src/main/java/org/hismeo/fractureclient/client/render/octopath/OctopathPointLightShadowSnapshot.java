package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Vector3f;

import java.util.Objects;

/**
 * GPU-facing result for one local source stored in the shared point-light depth atlas.
 *
 * <p>Every active local light owns six fixed atlas tiles. Sharing the texture keeps sixteen
 * point-light shadows within one fragment sampler instead of requiring ninety-six samplers.</p>
 */
record OctopathPointLightShadowSnapshot(
        Vector3f lightPosition,
        int depthAtlasTextureId,
        int resolution,
        float range,
        boolean available
) {
    static final int FACE_COUNT = 6;

    OctopathPointLightShadowSnapshot {
        lightPosition = new Vector3f(Objects.requireNonNull(lightPosition, "lightPosition"));
        if (available) {
            if (depthAtlasTextureId <= 0 || resolution <= 0 || !Float.isFinite(range) || range <= 0.0F) {
                throw new IllegalArgumentException("Available point-light shadow needs atlas, resolution and range");
            }
        }
    }

    @Override
    public Vector3f lightPosition() {
        return new Vector3f(lightPosition);
    }

    static OctopathPointLightShadowSnapshot unavailable() {
        return new OctopathPointLightShadowSnapshot(
                new Vector3f(),
                0,
                0,
                0.0F,
                false);
    }

}
