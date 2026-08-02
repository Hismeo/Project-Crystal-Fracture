package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Vector3f;

import java.util.Objects;

/**
 * A compact coloured aperture used only by the sunlight volume pass. It represents a cluster of
 * stained-glass blocks/panes, not an opaque shadow caster: direct sun remains able to travel
 * through the aperture while the air below receives its dye colour.
 */
record OctopathSunlightFilter(Vector3f position, Vector3f color, float radius, float score) {
    OctopathSunlightFilter {
        position = new Vector3f(Objects.requireNonNull(position, "position"));
        color = new Vector3f(Objects.requireNonNull(color, "color"));
        if (!Float.isFinite(radius) || radius <= 0.0F || !Float.isFinite(score)) {
            throw new IllegalArgumentException("Sunlight filter needs finite radius and score");
        }
    }

    @Override
    public Vector3f position() {
        return new Vector3f(position);
    }

    @Override
    public Vector3f color() {
        return new Vector3f(color);
    }
}
