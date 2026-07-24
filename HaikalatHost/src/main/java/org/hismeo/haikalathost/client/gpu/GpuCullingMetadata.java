package org.hismeo.haikalathost.client.gpu;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * std430 record consumed by the H6 compute culling and indirect-command compaction pass.
 *
 * <p>The first vec4 is a sphere in the same coordinate space as the captured vertex position.
 * The second ivec4 stores transform index, batch index, compact-output base and flags.</p>
 */
public final class GpuCullingMetadata {
    public static final int BYTES = 32;
    public static final int FLAG_FRUSTUM_SPHERE = 1;
    public static final int FLAG_COMPACT = 1 << 1;

    private GpuCullingMetadata() {
    }

    public static void writeTo(
            ByteBuffer target,
            float centerX,
            float centerY,
            float centerZ,
            float radius,
            int transformIndex,
            int batchIndex,
            int outputBase,
            int flags
    ) {
        Objects.requireNonNull(target, "target");
        if (target.remaining() < BYTES) {
            throw new IllegalArgumentException(
                    "culling metadata requires " + BYTES
                            + " bytes, found " + target.remaining());
        }
        if (!Float.isFinite(centerX)
                || !Float.isFinite(centerY)
                || !Float.isFinite(centerZ)
                || !Float.isFinite(radius)
                || radius < 0.0F) {
            throw new IllegalArgumentException("culling sphere must be finite and non-negative");
        }
        if (transformIndex < 0 || batchIndex < 0 || outputBase < 0) {
            throw new IllegalArgumentException("culling metadata indices must be non-negative");
        }
        target.putFloat(centerX);
        target.putFloat(centerY);
        target.putFloat(centerZ);
        target.putFloat(radius);
        target.putInt(transformIndex);
        target.putInt(batchIndex);
        target.putInt(outputBase);
        target.putInt(flags);
    }
}
