package org.hismeo.haikalathost.client.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;
import org.hismeo.haikalathost.client.geometry.CanonicalVertexLayout;
import org.hismeo.haikalathost.client.geometry.MinecraftCanonicalVertexEncoder;
import org.hismeo.haikalathost.client.geometry.MinecraftSequentialIndexEncoder;
import org.hismeo.haikalathost.client.submission.TakeoverFrameSubmission;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Owns chunk GPU data and publishes immutable generation swaps to the frame submitter. */
public final class LongLivedChunkMeshManager implements AutoCloseable {
    private final MinecraftInteropDiagnostics diagnostics;
    private final MinecraftCanonicalVertexEncoder vertexEncoder =
            new MinecraftCanonicalVertexEncoder();
    private final ChunkMeshArena arena = new ChunkMeshArena();
    private final ChunkArenaRetirementQueue retirement = new ChunkArenaRetirementQueue(arena);
    private final IdentityHashMap<Object, IdentityHashMap<RenderType, LongLivedChunkMesh>> sections =
            new IdentityHashMap<>();
    private LongLivedChunkMesh[] visibleScratch = new LongLivedChunkMesh[1024];
    private boolean closed;

    public LongLivedChunkMeshManager(MinecraftInteropDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public boolean upload(
            ChunkMeshRegistry.Capture capture,
            int originX,
            int originY,
            int originZ,
            MeshData meshData
    ) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        MeshData.DrawState state = meshData.drawState();
        if (state.vertexCount() <= 0 || state.indexCount() <= 0) {
            releaseLayer(capture.section(), capture.renderType());
            return false;
        }

        int vertexBytes =
                Math.multiplyExact(state.vertexCount(), CanonicalVertexLayout.STRIDE_BYTES);
        int indexElementBytes = state.indexType().bytes;
        int indexBytes = Math.multiplyExact(state.indexCount(), indexElementBytes);
        ByteBuffer canonicalVertices =
                MemoryUtil.memAlloc(vertexBytes).order(ByteOrder.nativeOrder());
        ByteBuffer canonicalIndices =
                MemoryUtil.memAlloc(indexBytes).order(ByteOrder.nativeOrder());
        ChunkMeshArena.Allocation allocation = null;
        try {
            vertexEncoder.encode(
                    state.format(), meshData.vertexBuffer(), state.vertexCount(), canonicalVertices);
            canonicalVertices.flip();

            ByteBuffer explicit = meshData.indexBuffer();
            if (explicit == null) {
                MinecraftSequentialIndexEncoder.encode(
                        state.mode(), state.indexCount(), state.indexType(), canonicalIndices);
            } else {
                canonicalIndices.put(exactSlice(explicit, indexBytes));
            }
            canonicalIndices.flip();

            int pagesBefore = arena.pageCount();
            allocation = arena.upload(canonicalVertices, canonicalIndices, indexElementBytes);
            if (!ChunkMeshRegistry.isCurrent(capture.vertexBuffer(), capture.generation())) {
                arena.release(allocation);
                allocation = null;
                return false;
            }

            LongLivedChunkMesh replacement = new LongLivedChunkMesh(
                    capture.section(),
                    capture.renderType(),
                    capture.generation(),
                    allocation,
                    state.mode().asGLMode,
                    state.indexCount(),
                    state.indexType().asGLType,
                    indexElementBytes,
                    originX,
                    originY,
                    originZ);
            allocation = null;
            IdentityHashMap<RenderType, LongLivedChunkMesh> layers =
                    sections.computeIfAbsent(
                            capture.section(), ignored -> new IdentityHashMap<>());
            retirement.retire(layers.put(capture.renderType(), replacement));
            diagnostics.recordGeometryUpload(vertexBytes, indexBytes);
            if (arena.pageCount() != pagesBefore) {
                HaikalatHost.LOGGER.info("H2 chunk arena pages={}, meshes={}, live={}B",
                        arena.pageCount(), meshCount(), arena.allocatedBytes());
            }

            return true;
        } finally {
            if (allocation != null) arena.release(allocation);
            MemoryUtil.memFree(canonicalIndices);
            MemoryUtil.memFree(canonicalVertices);
        }
    }

    public void submitLayer(
            RenderType renderType,
            Iterable<?> visibleSections,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4fc modelView,
            Matrix4fc projection,
            TakeoverFrameSubmission submission
    ) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        int visibleCount = 0;
        try {
            for (Object section : visibleSections) {
                Map<RenderType, LongLivedChunkMesh> layers = sections.get(section);
                if (layers == null) continue;
                LongLivedChunkMesh mesh = layers.get(renderType);
                if (mesh == null) continue;
                if (visibleCount == visibleScratch.length) {
                    visibleScratch = Arrays.copyOf(
                            visibleScratch, Math.multiplyExact(visibleScratch.length, 2));
                }
                visibleScratch[visibleCount++] = mesh;
            }
            submission.captureChunkLayer(
                    renderType,
                    visibleScratch,
                    visibleCount,
                    cameraX,
                    cameraY,
                    cameraZ,
                    modelView,
                    projection);
            diagnostics.recordChunkDraws(visibleCount);
        } finally {
            Arrays.fill(visibleScratch, 0, visibleCount, null);
        }
    }

    public void retainLayers(Object section, Set<RenderType> retained) {
        RenderSystem.assertOnRenderThread();
        IdentityHashMap<RenderType, LongLivedChunkMesh> layers = sections.get(section);
        if (layers == null) return;
        layers.entrySet().removeIf(entry -> {
            if (retained.contains(entry.getKey())) return false;
            retirement.retire(entry.getValue());
            return true;
        });
        if (layers.isEmpty()) sections.remove(section);
    }

    public void releaseLayer(Object section, RenderType renderType) {
        RenderSystem.assertOnRenderThread();
        IdentityHashMap<RenderType, LongLivedChunkMesh> layers = sections.get(section);
        if (layers == null) return;
        retirement.retire(layers.remove(renderType));
        if (layers.isEmpty()) sections.remove(section);
    }

    public void releaseSection(Object section) {
        RenderSystem.assertOnRenderThread();
        IdentityHashMap<RenderType, LongLivedChunkMesh> layers = sections.remove(section);
        if (layers != null) layers.values().forEach(retirement::retire);
    }

    public void invalidateAll() {
        RenderSystem.assertOnRenderThread();
        sections.values().forEach(layers -> layers.values().forEach(retirement::retire));
        sections.clear();
        vertexEncoder.clear();
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        retirement.endFrame();
    }

    public int meshCount() {
        int count = 0;
        for (Map<RenderType, LongLivedChunkMesh> layers : sections.values()) {
            count += layers.size();
        }
        return count;
    }

    public int pageCount() {
        return arena.pageCount();
    }

    public long allocatedBytes() {
        return arena.allocatedBytes();
    }

    @Override
    public void close() {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        sections.clear();
        retirement.close();
        arena.close();
        closed = true;
    }

    private static ByteBuffer exactSlice(ByteBuffer source, int bytes) {
        ByteBuffer result = source.duplicate().order(ByteOrder.nativeOrder());
        if (bytes < 0 || result.remaining() < bytes) {
            throw new IllegalArgumentException("chunk index payload is too small");
        }
        result.limit(result.position() + bytes);
        return result.slice().order(ByteOrder.nativeOrder());
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("long-lived chunk mesh manager is closed");
    }
}
