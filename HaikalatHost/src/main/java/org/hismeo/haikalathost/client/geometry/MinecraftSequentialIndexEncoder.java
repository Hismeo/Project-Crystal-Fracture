package org.hismeo.haikalathost.client.geometry;

import com.mojang.blaze3d.vertex.VertexFormat;

import java.nio.ByteBuffer;

public final class MinecraftSequentialIndexEncoder {
    private MinecraftSequentialIndexEncoder() {
    }

    public static void encode(
            VertexFormat.Mode mode,
            int indexCount,
            VertexFormat.IndexType indexType,
            ByteBuffer target
    ) {
        SequentialIndexPattern pattern = switch (mode) {
            case QUADS -> SequentialIndexPattern.QUADS;
            case LINES -> SequentialIndexPattern.LINES;
            default -> SequentialIndexPattern.IDENTITY;
        };
        CanonicalIndexType canonicalType = switch (indexType) {
            case SHORT -> CanonicalIndexType.UNSIGNED_SHORT;
            case INT -> CanonicalIndexType.UNSIGNED_INT;
        };
        CanonicalSequentialIndexEncoder.encode(pattern, indexCount, canonicalType, target);
    }
}
