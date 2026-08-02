package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

/** Immutable proxy scene for a single local point-light depth cube. */
record OctopathPointLightShadowFrame(
        Vector3f lightPosition,
        float range,
        List<OctopathDirectionalShadowCaster> casters
) {
    OctopathPointLightShadowFrame {
        lightPosition = new Vector3f(Objects.requireNonNull(lightPosition, "lightPosition"));
        casters = List.copyOf(Objects.requireNonNull(casters, "casters"));
        if (!Float.isFinite(lightPosition.x) || !Float.isFinite(lightPosition.y)
                || !Float.isFinite(lightPosition.z) || !Float.isFinite(range) || range <= 0.0F) {
            throw new IllegalArgumentException("Point-light shadow frame needs finite light position and range");
        }
    }

    @Override
    public Vector3f lightPosition() {
        return new Vector3f(lightPosition);
    }
}
