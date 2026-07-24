package org.hismeo.haikalathost.client.runtime;

import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.hismeo.haikalathost.client.chunk.ChunkMeshManager;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.compat.MinecraftRenderScope;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;
import org.hismeo.haikalathost.client.geometry.MinecraftGeometryUploader;
import org.hismeo.haikalathost.client.geometry.MinecraftIndexPayload;
import org.hismeo.haikalathost.client.geometry.MinecraftVertexFormatTranslator;
import org.hismeo.haikalathost.client.geometry.MinecraftVertexLayout;
import org.hismeo.haikalathost.client.geometry.UnsupportedVertexFormatException;
import org.hismeo.haikalathost.client.routing.DrawRoute;
import org.hismeo.haikalathost.client.routing.FallbackReason;

import org.hismeo.haikalathost.client.submission.MinecraftDrawScheduler;
import java.nio.ByteBuffer;
import java.util.Objects;

public final class MinecraftHaikalatRuntime implements AutoCloseable {
    private final GlRenderDevice renderDevice;
    private final MinecraftInteropDiagnostics diagnostics;
    private final MinecraftVertexFormatTranslator vertexFormats;
    private final MinecraftGeometryUploader geometryUploader;
    private final ChunkMeshManager chunkMeshes;
    private final MinecraftDrawScheduler drawScheduler = new MinecraftDrawScheduler();
    private volatile boolean resourceReloadInProgress;
    private boolean acceptingDraws = true;
    private boolean closed;

    public MinecraftHaikalatRuntime(MinecraftInteropDiagnostics diagnostics) {
        RenderSystem.assertOnRenderThread();
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.renderDevice = new GlRenderDevice();
        this.vertexFormats = new MinecraftVertexFormatTranslator();
        this.geometryUploader = new MinecraftGeometryUploader(renderDevice, diagnostics);
        this.chunkMeshes = new ChunkMeshManager(renderDevice, diagnostics, vertexFormats);
    }

    public DrawRoute route(RenderType renderType, MeshData.DrawState drawState) {
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(drawState, "drawState");

        if (!HaikalatHostConfiguration.current().immediateCompatibility()
                || closed || !acceptingDraws) {
            diagnostics.recordFallback(FallbackReason.DISABLED, renderType, drawState, "runtime disabled");
            return DrawRoute.VANILLA;
        }
        if (resourceReloadInProgress) {
            diagnostics.recordFallback(
                    FallbackReason.RESOURCE_RELOAD_IN_PROGRESS, renderType, drawState, "resource reload");
            return DrawRoute.VANILLA;
        }
        if (!RenderSystem.isOnRenderThread()) {
            diagnostics.recordFallback(
                    FallbackReason.RUNTIME_NOT_READY, renderType, drawState, "not on render thread");
            return DrawRoute.VANILLA;
        }
        if (!isSupportedMode(drawState.mode())) {
            diagnostics.recordFallback(
                    FallbackReason.UNSUPPORTED_VERTEX_MODE, renderType, drawState, drawState.mode().name());
            return DrawRoute.VANILLA;
        }

        MinecraftVertexLayout layout;
        try {
            layout = vertexFormats.translate(drawState.format());
        } catch (UnsupportedVertexFormatException exception) {
            diagnostics.recordFallback(
                    FallbackReason.UNSUPPORTED_VERTEX_ELEMENT, renderType, drawState, exception.getMessage());
            return DrawRoute.VANILLA;
        }

        try {
            if (!geometryUploader.canAllocate(drawState, layout)) {
                diagnostics.recordFallback(
                        FallbackReason.ARENA_OVERFLOW, renderType, drawState, "frame slot capacity exhausted");
                return DrawRoute.VANILLA;
            }
        } catch (ArithmeticException exception) {
            diagnostics.recordFallback(
                    FallbackReason.ARENA_OVERFLOW, renderType, drawState, exception.getMessage());
            return DrawRoute.VANILLA;
        }
        return DrawRoute.HAIKALAT_COMPAT;
    }

    /**
     * Takes ownership of {@code meshData}; after this method starts, vanilla must not consume it.
     */
    public void drawOwned(RenderType renderType, MeshData meshData) {
        RenderSystem.assertOnRenderThread();
        try (meshData) {
            MeshData.DrawState drawState = meshData.drawState();
            MinecraftVertexLayout layout = vertexFormats.translate(drawState.format());
            MinecraftIndexPayload indices = indexPayload(meshData, drawState);
            try (MinecraftRenderScope ignored = MinecraftRenderScope.open(renderType, drawState.mode())) {
                geometryUploader.uploadAndDraw(drawState, meshData.vertexBuffer(), indices, layout);
            }
        } catch (Throwable throwable) {
            acceptingDraws = false;
            diagnostics.recordFailure(throwable);
            throw propagate(throwable);
        } finally {
            try {
                geometryUploader.releaseBindings();
            } finally {
                BufferUploader.invalidate();
                renderDevice.invalidateState();
            }
        }
    }

    public void beginResourceReload() {
        resourceReloadInProgress = true;
    }

    public void endResourceReload() {
        RenderSystem.assertOnRenderThread();
        invalidateResources();
        resourceReloadInProgress = false;
        if (!closed) acceptingDraws = true;
    }

    public void invalidateResources() {
        RenderSystem.assertOnRenderThread();
        vertexFormats.clear();
        geometryUploader.invalidateVertexFormatState();
        chunkMeshes.invalidateAll();
        drawScheduler.clearPipelineIdentities();
        renderDevice.invalidateState();
        BufferUploader.invalidate();
    }

    public boolean mirrorChunkMesh(VertexBuffer vanillaBuffer, long generation, MeshData meshData) {
        RenderSystem.assertOnRenderThread();
        if (resourceReloadInProgress || closed) return false;
        return chunkMeshes.mirrorUpload(vanillaBuffer, generation, meshData);
    }

    public boolean drawChunkMesh(VertexBuffer vanillaBuffer) {
        RenderSystem.assertOnRenderThread();
        if (resourceReloadInProgress || closed) return false;
        return chunkMeshes.draw(vanillaBuffer);
    }

    public void releaseChunkBuffer(VertexBuffer vanillaBuffer) {
        RenderSystem.assertOnRenderThread();
        if (!closed) chunkMeshes.release(vanillaBuffer);
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        if (!closed) {
            geometryUploader.endFrame();
            chunkMeshes.endFrame();
        }
    }

    public void releaseWorldResources() {
        chunkMeshes.invalidateAll();
    }

    @Override
    public void close() {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        acceptingDraws = false;
        chunkMeshes.close();
        geometryUploader.close();
        renderDevice.invalidateState();
        closed = true;
        diagnostics.logSummary();
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

    private static boolean isSupportedMode(VertexFormat.Mode mode) {
        return switch (mode) {
            case LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP,
                    TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN, QUADS -> true;
        };
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) return runtimeException;
        if (throwable instanceof Error error) throw error;
        return new RuntimeException("Haikalat Minecraft draw failed", throwable);
    }
}
