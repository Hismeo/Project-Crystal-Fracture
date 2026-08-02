package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Vector3f;

/**
 * A compact, stable local-light source useful to the HD-2D post pass. Dense adjacent emissive
 * blocks may share one source key so the renderer does not keep swapping them in and out.
 */
record OctopathLocalLight(Vector3f position, Vector3f color, float power, float score, long stableKey) {
    OctopathLocalLight {
        position = new Vector3f(position);
        color = new Vector3f(color);
    }
}
