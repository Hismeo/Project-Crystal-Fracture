package org.hismeo.haikalathost.client.material;

import java.nio.ByteBuffer;
import java.util.Objects;

/** std430-compatible 32-byte material payload. */
public record GpuMaterial(
        long baseColorHandle,
        long normalHandle,
        int samplerId,
        int flags,
        float alphaCutoff,
        int tintMode
) {
    public static final int BYTES = 32;

    public GpuMaterial {
        if (samplerId < 0) throw new IllegalArgumentException("samplerId must not be negative");
        if ((flags & ~MaterialFeature.ALL_MASK) != 0) {
            throw new IllegalArgumentException("unknown material feature bits: " + flags);
        }
        if (!Float.isFinite(alphaCutoff) || alphaCutoff < 0.0F || alphaCutoff > 1.0F) {
            throw new IllegalArgumentException("alphaCutoff must be finite and in [0, 1]");
        }
    }

    public void writeTo(ByteBuffer target) {
        Objects.requireNonNull(target, "target");
        if (target.remaining() < BYTES) {
            throw new IllegalArgumentException("material target has fewer than 32 writable bytes");
        }
        target.putLong(baseColorHandle);
        target.putLong(normalHandle);
        target.putInt(samplerId);
        target.putInt(flags);
        target.putFloat(alphaCutoff);
        target.putInt(tintMode);
    }
}
