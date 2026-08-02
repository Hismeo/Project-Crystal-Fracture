package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Vector3f;

/**
 * One world-space receiver point for the small, soft grounding shadow drawn by the HD-2D pass.
 *
 * <p>The point is the actual block hit beneath an entity rather than the entity's feet. That
 * lets the shader stay glued to stepped terrain and naturally fade a shadow while an entity is
 * airborne.</p>
 */
record OctopathEntityGroundShadow(Vector3f groundPosition, float radius, float opacity) {
    OctopathEntityGroundShadow {
        groundPosition = new Vector3f(groundPosition);
        radius = finiteClamped(radius, 0.15F, 3.0F);
        opacity = finiteClamped(opacity, 0.0F, 1.0F);
    }

    private static float finiteClamped(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
