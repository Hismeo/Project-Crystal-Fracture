package org.hismeo.haikalathost.client.gpu;

import java.nio.ByteBuffer;
import java.util.Objects;

/** std430-compatible per-draw payload addressed through baseInstance. */
public record GpuDrawData(
        int transformIndex,
        int materialIndex,
        int objectFlags,
        float originX,
        float originY,
        float originZ
) {
    public static final int BYTES = 32;

    public GpuDrawData {
        if (transformIndex < 0) throw new IllegalArgumentException("transformIndex must not be negative");
        if (materialIndex < 0) throw new IllegalArgumentException("materialIndex must not be negative");
        if (!Float.isFinite(originX) || !Float.isFinite(originY) || !Float.isFinite(originZ)) {
            throw new IllegalArgumentException("draw origin must be finite");
        }
    }

    public void writeTo(ByteBuffer target) {
        writeTo(target, transformIndex, materialIndex, objectFlags, originX, originY, originZ);
    }

    public static void writeTo(
            ByteBuffer target,
            int transformIndex,
            int materialIndex,
            int objectFlags,
            float originX,
            float originY,
            float originZ
    ) {
        Objects.requireNonNull(target, "target");
        if (transformIndex < 0) throw new IllegalArgumentException("transformIndex must not be negative");
        if (materialIndex < 0) throw new IllegalArgumentException("materialIndex must not be negative");
        if (!Float.isFinite(originX) || !Float.isFinite(originY) || !Float.isFinite(originZ)) {
            throw new IllegalArgumentException("draw origin must be finite");
        }
        if (target.remaining() < BYTES) {
            throw new IllegalArgumentException("draw-data target has fewer than 32 writable bytes");
        }
        target.putInt(transformIndex);
        target.putInt(materialIndex);
        target.putInt(objectFlags);
        target.putInt(0);
        target.putFloat(originX);
        target.putFloat(originY);
        target.putFloat(originZ);
        target.putFloat(0.0F);
    }
}
