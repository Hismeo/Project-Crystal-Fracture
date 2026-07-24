package org.hismeo.haikalathost.client.geometry;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import com.kaleblangley.haikalat.backend.sync.GpuFence;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT;

/**
 * Persistent-mapped, frame-slotted storage for immediate Minecraft geometry.
 */
public final class TransientGeometryArena implements AutoCloseable {
    private static final int DEFAULT_VERTEX_MIB = 16;
    private static final int DEFAULT_INDEX_MIB = 4;
    private static final int DEFAULT_FRAME_SLOTS = 3;
    private static final int STORAGE_FLAGS =
            GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_DYNAMIC_STORAGE_BIT;
    private static final int MAP_FLAGS =
            GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;

    private final MinecraftInteropDiagnostics diagnostics;
    private final GlBuffer vertexBuffer;
    private final GlBuffer indexBuffer;
    private final ByteBuffer mappedVertices;
    private final ByteBuffer mappedIndices;
    private final GpuRingAllocator vertexAllocator;
    private final GpuRingAllocator indexAllocator;
    private final GpuFence[] slotFences;
    private boolean slotPrepared;
    private boolean slotUsed;
    private boolean closed;

    public TransientGeometryArena(MinecraftInteropDiagnostics diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        int frameSlots = positiveProperty("haikalathost.arena.frameSlots", DEFAULT_FRAME_SLOTS);
        if (frameSlots < 2) {
            throw new IllegalArgumentException("haikalathost.arena.frameSlots must be at least two");
        }
        int vertexSlotBytes = mebibytes("haikalathost.arena.vertexMiB", DEFAULT_VERTEX_MIB);
        int indexSlotBytes = mebibytes("haikalathost.arena.indexMiB", DEFAULT_INDEX_MIB);
        this.vertexAllocator = new GpuRingAllocator(vertexSlotBytes, frameSlots);
        this.indexAllocator = new GpuRingAllocator(indexSlotBytes, frameSlots);
        this.slotFences = new GpuFence[frameSlots];

        GlBuffer createdVertices = null;
        GlBuffer createdIndices = null;
        try {
            createdVertices = GlBuffer.arrayBuffer(GL_DYNAMIC_DRAW)
                    .allocateStorage(vertexAllocator.totalBytes(), STORAGE_FLAGS);
            createdIndices = GlBuffer.elementArrayBuffer(GL_DYNAMIC_DRAW)
                    .allocateStorage(indexAllocator.totalBytes(), STORAGE_FLAGS);
            ByteBuffer vertices = createdVertices.mapRange(
                    0L, vertexAllocator.totalBytes(), MAP_FLAGS);
            ByteBuffer indices = createdIndices.mapRange(
                    0L, indexAllocator.totalBytes(), MAP_FLAGS);
            if (vertices == null || indices == null) {
                throw new IllegalStateException("OpenGL returned null for persistent arena mapping");
            }
            this.vertexBuffer = createdVertices;
            this.indexBuffer = createdIndices;
            this.mappedVertices = vertices;
            this.mappedIndices = indices;
        } catch (Throwable throwable) {
            if (createdIndices != null) createdIndices.close();
            if (createdVertices != null) createdVertices.close();
            throw throwable;
        }
    }

    public boolean canAllocate(int vertexBytes, int vertexAlignment, int indexBytes) {
        ensureOpen();
        if (!tryPrepareSlot()) return false;
        return vertexAllocator.canAllocate(vertexBytes, vertexAlignment)
                && indexAllocator.canAllocate(indexBytes, Integer.BYTES);
    }

    public Allocation upload(ByteBuffer vertices, int vertexAlignment, ByteBuffer indices) {
        ensureOpen();
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(indices, "indices");
        if (!tryPrepareSlot()) {
            throw new IllegalStateException("Transient arena slot is still in use by the GPU");
        }

        GpuRingAllocator.Allocation vertexAllocation =
                vertexAllocator.allocate(vertices.remaining(), vertexAlignment);
        GpuRingAllocator.Allocation indexAllocation =
                indexAllocator.allocate(indices.remaining(), Integer.BYTES);
        copy(vertices, mappedVertices, vertexAllocation);
        copy(indices, mappedIndices, indexAllocation);
        slotUsed = true;
        return new Allocation(vertexAllocation.offsetBytes(), indexAllocation.offsetBytes());
    }
    /** Uploads vertices while leaving indices in a reusable shared GPU buffer. */
    public Allocation uploadVertices(ByteBuffer vertices, int vertexAlignment) {
        ensureOpen();
        Objects.requireNonNull(vertices, "vertices");
        if (!tryPrepareSlot()) {
            throw new IllegalStateException("Transient arena slot is still in use by the GPU");
        }

        GpuRingAllocator.Allocation vertexAllocation =
                vertexAllocator.allocate(vertices.remaining(), vertexAlignment);
        copy(vertices, mappedVertices, vertexAllocation);
        slotUsed = true;
        return new Allocation(vertexAllocation.offsetBytes(), 0L);
    }


    public GlBuffer vertexBuffer() {
        ensureOpen();
        return vertexBuffer;
    }

    public GlBuffer indexBuffer() {
        ensureOpen();
        return indexBuffer;
    }

    public void endFrame() {
        ensureOpen();
        if (!slotUsed) return;

        int slot = vertexAllocator.currentSlot();
        if (slot != indexAllocator.currentSlot()) {
            throw new IllegalStateException("Transient arena allocators lost frame-slot synchronization");
        }
        if (slotFences[slot] != null) {
            throw new IllegalStateException("Transient arena slot was reused before its fence completed");
        }

        slotFences[slot] = GpuFence.insert();
        vertexAllocator.advanceFrame();
        indexAllocator.advanceFrame();
        slotPrepared = false;
        slotUsed = false;
    }

    /**
     * Never waits for the GPU. A busy slot makes the compatibility route fall
     * back to vanilla for this frame instead of turning GPU back-pressure into
     * a visible render-thread latency spike.
     */
    private boolean tryPrepareSlot() {
        if (slotPrepared) return true;
        int slot = vertexAllocator.currentSlot();
        if (slot != indexAllocator.currentSlot()) {
            throw new IllegalStateException("Transient arena allocators have different current slots");
        }

        GpuFence fence = slotFences[slot];
        if (fence != null) {
            if (!fence.isSignaled()) return false;
            fence.close();
            slotFences[slot] = null;
        }
        slotPrepared = true;
        return true;
    }


    private static void copy(ByteBuffer source, ByteBuffer mapped,
                             GpuRingAllocator.Allocation allocation) {
        ByteBuffer input = source.duplicate();
        if (input.remaining() != allocation.lengthBytes()) {
            throw new IllegalArgumentException("Arena allocation length does not match source payload");
        }
        ByteBuffer destination = mapped.duplicate();
        destination.position(allocation.offsetBytes());
        destination.limit(Math.addExact(allocation.offsetBytes(), allocation.lengthBytes()));
        destination.slice().put(input);
    }

    @Override
    public void close() {
        if (closed) return;
        for (int slot = 0; slot < slotFences.length; slot++) {
            GpuFence fence = slotFences[slot];
            if (fence != null) {
                fence.close();
                slotFences[slot] = null;
            }
        }
        vertexBuffer.unmap();
        indexBuffer.unmap();
        indexBuffer.close();
        vertexBuffer.close();
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Transient geometry arena is closed");
    }

    private static int mebibytes(String property, int fallback) {
        return Math.multiplyExact(positiveProperty(property, fallback), 1024 * 1024);
    }

    private static int positiveProperty(String property, int fallback) {
        int value = Integer.getInteger(property, fallback);
        if (value <= 0) throw new IllegalArgumentException(property + " must be positive");
        return value;
    }

    public record Allocation(long vertexOffsetBytes, long indexOffsetBytes) {
    }
}
