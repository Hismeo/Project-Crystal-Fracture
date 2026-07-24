package org.hismeo.haikalathost.client.geometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public final class CanonicalSequentialIndexEncoder {
    private CanonicalSequentialIndexEncoder() {
    }

    public static void encode(
            SequentialIndexPattern pattern,
            int indexCount,
            CanonicalIndexType indexType,
            ByteBuffer target
    ) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(indexType, "indexType");
        Objects.requireNonNull(target, "target");
        if (indexCount < 0) throw new IllegalArgumentException("indexCount must not be negative");
        if ((pattern == SequentialIndexPattern.QUADS || pattern == SequentialIndexPattern.LINES)
                && indexCount % 6 != 0) {
            throw new IllegalArgumentException(pattern + " index count must be divisible by six");
        }
        int required = Math.multiplyExact(indexCount, indexType.bytes());
        if (target.remaining() < required) {
            throw new IllegalArgumentException("index target has fewer than " + required + " bytes");
        }
        int maxIndex = switch (pattern) {
            case QUADS, LINES -> indexCount == 0 ? -1 : indexCount / 6 * 4 - 1;
            case IDENTITY -> indexCount - 1;
        };
        if (indexType == CanonicalIndexType.UNSIGNED_SHORT && maxIndex > 0xFFFF) {
            throw new IllegalArgumentException("16-bit sequential indices exceed 65535 vertices");
        }

        ByteBuffer output = target.order(ByteOrder.nativeOrder());
        if (pattern == SequentialIndexPattern.IDENTITY) {
            for (int index = 0; index < indexCount; index++) put(output, indexType, index);
            return;
        }
        for (int written = 0, base = 0; written < indexCount; written += 6, base += 4) {
            if (pattern == SequentialIndexPattern.QUADS) {
                put(output, indexType, base);
                put(output, indexType, base + 1);
                put(output, indexType, base + 2);
                put(output, indexType, base + 2);
                put(output, indexType, base + 3);
                put(output, indexType, base);
            } else {
                put(output, indexType, base);
                put(output, indexType, base + 1);
                put(output, indexType, base + 2);
                put(output, indexType, base + 3);
                put(output, indexType, base + 2);
                put(output, indexType, base + 1);
            }
        }
    }

    private static void put(ByteBuffer target, CanonicalIndexType type, int index) {
        if (type == CanonicalIndexType.UNSIGNED_SHORT) target.putShort((short) index);
        else target.putInt(index);
    }
}
