package org.hismeo.fractureclient.client.weapon;

import org.joml.Vector3f;

import java.util.Objects;

/** One immutable world-space sample of the schema-exported weapon trail edge. */
public record WeaponTrailSegment(Vector3f start, Vector3f end) {
    public WeaponTrailSegment {
        start = new Vector3f(Objects.requireNonNull(start, "start"));
        end = new Vector3f(Objects.requireNonNull(end, "end"));
    }

    @Override
    public Vector3f start() {
        return new Vector3f(start);
    }

    @Override
    public Vector3f end() {
        return new Vector3f(end);
    }
}
