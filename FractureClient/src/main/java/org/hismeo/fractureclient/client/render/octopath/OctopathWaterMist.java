package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Vector3f;

/**
 * A clustered, visible water-surface sample used to place a very local world-space mist layer.
 *
 * <p>This deliberately represents world geometry rather than a blue-pixel heuristic: the final
 * Minecraft target has no material identifier, so identifying water from its colour would also
 * catch shadows, stained glass and many modded blocks.</p>
 */
record OctopathWaterMist(Vector3f position, float radius, float coverage) {
    OctopathWaterMist {
        position = new Vector3f(position);
        radius = finiteClamped(radius, 1.0F, 24.0F);
        coverage = finiteClamped(coverage, 0.0F, 1.0F);
    }

    private static float finiteClamped(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
