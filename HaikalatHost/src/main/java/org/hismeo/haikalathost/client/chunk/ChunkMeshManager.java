package org.hismeo.haikalathost.client.chunk;

import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;
import org.hismeo.haikalathost.client.geometry.MinecraftIndexPayload;
import org.hismeo.haikalathost.client.geometry.MinecraftVertexFormatTranslator;
import org.hismeo.haikalathost.client.geometry.MinecraftVertexLayout;
import org.hismeo.haikalathost.client.geometry.SequentialIndexCache;

import org.hismeo.haikalathost.client.runtime.HaikalatHostConfiguration;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ChunkMeshManager implements AutoCloseable {
    private final GlRenderDevice renderDevice;
    private final MinecraftInteropDiagnostics diagnostics;
    private final MinecraftVertexFormatTranslator vertexFormats;
    private final SequentialIndexCache sequentialIndices;
    private final DeferredChunkFreeQueue deferredFree = new DeferredChunkFreeQueue();
    private final Map<VertexBuffer, ChunkMeshHandle> handles = new IdentityHashMap<>();
    private final CommandBuffer drawCommands;
    private boolean routingEnabled = true;
    private boolean closed;

    public ChunkMeshManager(GlRenderDevice renderDevice,
                            MinecraftInteropDiagnostics diagnostics,
                            MinecraftVertexFormatTranslator vertexFormats) {
        this.renderDevice = renderDevice;
        this.diagnostics = diagnostics;
        this.vertexFormats = vertexFormats;
        this.sequentialIndices = new SequentialIndexCache(diagnostics);
        this.drawCommands = renderDevice.createCommandBuffer();
    }

    public boolean mirrorUpload(VertexBuffer vanillaBuffer, long generation, MeshData meshData) {
        RenderSystem.assertOnRenderThread();
        if (!enabled() || meshData.drawState().mode() != VertexFormat.Mode.QUADS) {
            release(vanillaBuffer);
            return false;
        }

        try {
            MeshData.DrawState drawState = meshData.drawState();
            MinecraftVertexLayout layout = vertexFormats.translate(drawState.format());
            MinecraftIndexPayload indices = indexPayload(meshData, drawState);
            ChunkMeshHandle replacement = new ChunkMeshHandle(
                    generation, drawState, meshData.vertexBuffer(), indices, layout,
                    sequentialIndices, diagnostics);
            ChunkMeshHandle previous = handles.put(vanillaBuffer, replacement);
            deferredFree.defer(previous);
            return true;
        } catch (Throwable throwable) {
            routingEnabled = false;
            diagnostics.recordFailure(throwable);
            ChunkMeshHandle previous = handles.remove(vanillaBuffer);
            deferredFree.defer(previous);
            return false;
        }
    }

    public boolean draw(VertexBuffer vanillaBuffer) {
        RenderSystem.assertOnRenderThread();
        if (!enabled()) return false;
        ChunkMeshHandle handle = handles.get(vanillaBuffer);
        if (handle == null) return false;

        handle.draw(renderDevice, drawCommands);
        diagnostics.recordChunkDraw();
        return true;
    }

    public void release(VertexBuffer vanillaBuffer) {
        RenderSystem.assertOnRenderThread();
        deferredFree.defer(handles.remove(vanillaBuffer));
    }

    public void invalidateAll() {
        RenderSystem.assertOnRenderThread();
        handles.values().forEach(deferredFree::defer);
        handles.clear();
        renderDevice.invalidateState();
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        deferredFree.drainSignaled();
    }

    private boolean enabled() {
        return !closed
                && routingEnabled
                && HaikalatHostConfiguration.current().chunkMirroring();
    }

    @Override
    public void close() {
        if (closed) return;
        handles.values().forEach(ChunkMeshHandle::close);
        handles.clear();
        deferredFree.close();
        sequentialIndices.close();
        closed = true;
    }

    private static MinecraftIndexPayload indexPayload(MeshData meshData, MeshData.DrawState drawState) {
        ByteBuffer explicit = meshData.indexBuffer();
        if (explicit != null) {
            return new MinecraftIndexPayload.Explicit(
                    explicit, drawState.indexCount(), drawState.indexType().asGLType);
        }
        return new MinecraftIndexPayload.Sequential(
                drawState.mode(),
                drawState.vertexCount(),
                drawState.indexCount(),
                drawState.indexType().asGLType);
    }
}
