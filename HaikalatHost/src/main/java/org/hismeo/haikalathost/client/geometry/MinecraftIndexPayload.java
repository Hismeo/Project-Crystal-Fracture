package org.hismeo.haikalathost.client.geometry;

import com.mojang.blaze3d.vertex.VertexFormat;

import java.nio.ByteBuffer;

public sealed interface MinecraftIndexPayload {
    record Explicit(ByteBuffer data, int indexCount, int glType) implements MinecraftIndexPayload {
        public Explicit {
            data = data.duplicate();
        }
    }

    record Sequential(
            VertexFormat.Mode mode,
            int vertexCount,
            int indexCount,
            int glType
    ) implements MinecraftIndexPayload {
    }
}
