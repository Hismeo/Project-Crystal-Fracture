package org.hismeo.haikalathost.client.scene;

public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    public Bounds {
        requireFinite(minX, "minX");
        requireFinite(minY, "minY");
        requireFinite(minZ, "minZ");
        requireFinite(maxX, "maxX");
        requireFinite(maxY, "maxY");
        requireFinite(maxZ, "maxZ");
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("bounds minimum must not exceed maximum");
        }
    }

    private static void requireFinite(float value, String label) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
    }
}
