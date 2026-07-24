package org.hismeo.haikalathost.client.staticmesh;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Native CPU copy retained until a captured Minecraft VertexBuffer is released. */
final class StaticMeshPayload implements AutoCloseable {
    private final VertexFormat format;
    private final int glMode;
    private final int indexCount;
    private final int glIndexType;
    private final int indexElementBytes;
    private final ByteBuffer vertices;
    private final ByteBuffer indices;
    private boolean closed;

    StaticMeshPayload(
            VertexFormat format,
            int glMode,
            int indexCount,
            int glIndexType,
            int indexElementBytes,
            ByteBuffer vertices,
            ByteBuffer indices
    ) {
        this.format = Objects.requireNonNull(format, "format");
        this.vertices = Objects.requireNonNull(vertices, "vertices");
        this.indices = Objects.requireNonNull(indices, "indices");
        if (indexCount <= 0) throw new IllegalArgumentException("indexCount must be positive");
        if (indexElementBytes != Short.BYTES && indexElementBytes != Integer.BYTES) {
            throw new IllegalArgumentException(
                    "unsupported index element size " + indexElementBytes);
        }
        this.glMode = glMode;
        this.indexCount = indexCount;
        this.glIndexType = glIndexType;
        this.indexElementBytes = indexElementBytes;
    }

    VertexFormat format() {
        ensureOpen();
        return format;
    }

    int glMode() {
        ensureOpen();
        return glMode;
    }

    int indexCount() {
        ensureOpen();
        return indexCount;
    }

    int glIndexType() {
        ensureOpen();
        return glIndexType;
    }

    int indexElementBytes() {
        ensureOpen();
        return indexElementBytes;
    }

    ByteBuffer vertices() {
        ensureOpen();
        return vertices.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    ByteBuffer indices() {
        ensureOpen();
        return indices.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    void replaceIndices(ByteBuffer source) {
        ensureOpen();
        ByteBuffer input = source.duplicate();
        if (input.remaining() != indices.capacity()) {
            throw new IllegalArgumentException(
                    "static index replacement requires " + indices.capacity()
                            + " bytes, found " + input.remaining());
        }
        indices.clear();
        indices.put(input).flip();
    }

    @Override
    public void close() {
        if (closed) return;
        MemoryUtil.memFree(indices);
        MemoryUtil.memFree(vertices);
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("static mesh payload is closed");
    }
}
