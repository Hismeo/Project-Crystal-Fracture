package org.hismeo.haikalathost.client.chunk;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import org.hismeo.haikalathost.client.geometry.CanonicalVertexLayout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;

/** Page-based Host-owned long-lived storage for canonical chunk vertices and indices. */
final class ChunkMeshArena implements AutoCloseable {
    private static final int DEFAULT_VERTEX_PAGE_BYTES = 64 * 1024 * 1024;
    private static final int DEFAULT_INDEX_PAGE_BYTES = 32 * 1024 * 1024;
    private final int vertexPageBytes;
    private final int indexPageBytes;
    private final List<Page> pages = new ArrayList<>();
    private boolean closed;

    ChunkMeshArena() {
        this(positiveProperty("haikalathost.chunkVertexPageBytes", DEFAULT_VERTEX_PAGE_BYTES),
                positiveProperty("haikalathost.chunkIndexPageBytes", DEFAULT_INDEX_PAGE_BYTES));
    }

    ChunkMeshArena(int vertexPageBytes, int indexPageBytes) {
        if (vertexPageBytes <= 0 || indexPageBytes <= 0) {
            throw new IllegalArgumentException("chunk arena page capacities must be positive");
        }
        this.vertexPageBytes = vertexPageBytes;
        this.indexPageBytes = indexPageBytes;
    }

    Allocation upload(ByteBuffer vertices, ByteBuffer indices, int indexAlignment) {
        ensureOpen();
        int vertexBytes = vertices.remaining();
        int indexBytes = indices.remaining();
        if (vertexBytes <= 0 || indexBytes <= 0) {
            throw new IllegalArgumentException("chunk mesh payloads must not be empty");
        }
        Allocation allocation = null;
        for (Page page : pages) {
            allocation = page.tryAllocate(vertexBytes, indexBytes, indexAlignment);
            if (allocation != null) break;
        }
        if (allocation == null) {
            Page page = createPage(vertexBytes, indexBytes);
            allocation = page.tryAllocate(vertexBytes, indexBytes, indexAlignment);
            if (allocation == null) {
                throw new IllegalStateException("new chunk page could not satisfy allocation");
            }
        }
        try {
            allocation.page.vertexBuffer.update(allocation.vertex.offsetBytes(), vertices);
            allocation.page.indexBuffer.update(allocation.index.offsetBytes(), indices);
            return allocation;
        } catch (Throwable failure) {
            release(allocation);
            throw failure;
        }
    }

    void release(Allocation allocation) {
        if (allocation != null) allocation.page.release(allocation);
    }

    int pageCount() {
        return pages.size();
    }

    long allocatedBytes() {
        long total = 0L;
        for (Page page : pages) {
            total += page.vertices.allocatedBytes();
            total += page.indices.allocatedBytes();
        }
        return total;
    }

    private Page createPage(int requiredVertexBytes, int requiredIndexBytes) {
        int vertexCapacity = pageCapacity(
                vertexPageBytes, requiredVertexBytes, CanonicalVertexLayout.STRIDE_BYTES);
        int indexCapacity = pageCapacity(indexPageBytes, requiredIndexBytes, Integer.BYTES);
        Page page = new Page(pages.size(), vertexCapacity, indexCapacity);
        pages.add(page);
        return page;
    }

    @Override
    public void close() {
        if (closed) return;
        for (Page page : pages) page.close();
        pages.clear();
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("chunk mesh arena is closed");
    }

    private static int positiveProperty(String key, int fallback) {
        int value = Integer.getInteger(key, fallback);
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    private static int pageCapacity(int preferred, int required, int alignment) {
        int aligned = align(required, alignment);
        if (aligned <= preferred) return preferred;
        int value = 1;
        while (value < aligned) value = Math.multiplyExact(value, 2);
        return value;
    }

    private static int align(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : Math.addExact(value, alignment - remainder);
    }

    static final class Page implements AutoCloseable {
        private final int id;
        private final GlBuffer vertexBuffer;
        private final GlBuffer indexBuffer;
        private final ChunkArenaAllocator vertices;
        private final ChunkArenaAllocator indices;

        Page(int id, int vertexCapacity, int indexCapacity) {
            this.id = id;
            vertexBuffer = GlBuffer.arrayBuffer(GL_DYNAMIC_DRAW);
            indexBuffer = GlBuffer.elementArrayBuffer(GL_DYNAMIC_DRAW);
            try {
                vertexBuffer.allocate(vertexCapacity);
                indexBuffer.allocate(indexCapacity);
            } catch (Throwable failure) {
                indexBuffer.close();
                vertexBuffer.close();
                throw failure;
            }
            vertices = new ChunkArenaAllocator(vertexCapacity);
            indices = new ChunkArenaAllocator(indexCapacity);
        }

        Allocation tryAllocate(int vertexBytes, int indexBytes, int indexAlignment) {
            ChunkArenaAllocator.Allocation vertex =
                    vertices.allocate(vertexBytes, CanonicalVertexLayout.STRIDE_BYTES);
            if (vertex == null) return null;
            ChunkArenaAllocator.Allocation index = indices.allocate(indexBytes, indexAlignment);
            if (index == null) {
                vertices.release(vertex);
                return null;
            }
            return new Allocation(this, vertex, index);
        }

        void release(Allocation allocation) {
            if (allocation.page != this) {
                throw new IllegalArgumentException("allocation belongs to another chunk page");
            }
            vertices.release(allocation.vertex);
            indices.release(allocation.index);
        }

        int id() {
            return id;
        }

        GlBuffer vertexBuffer() {
            return vertexBuffer;
        }

        GlBuffer indexBuffer() {
            return indexBuffer;
        }

        @Override
        public void close() {
            indexBuffer.close();
            vertexBuffer.close();
        }
    }

    record Allocation(
            Page page,
            ChunkArenaAllocator.Allocation vertex,
            ChunkArenaAllocator.Allocation index
    ) {
    }
}
