package org.hismeo.haikalathost.client.geometry;

import com.kaleblangley.haikalat.backend.vertex.VertexArray;
import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.mojang.blaze3d.vertex.MeshData;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL41.glVertexAttribLPointer;
import static org.lwjgl.opengl.GL32.glDrawElementsBaseVertex;

public final class MinecraftGeometryUploader implements AutoCloseable {
    private final GlRenderDevice renderDevice;
    private final MinecraftInteropDiagnostics diagnostics;
    private final Map<MinecraftVertexLayout, VertexArray> vertexArrays = new HashMap<>();
    private final SequentialIndexCache sequentialIndices;
    private final TransientGeometryArena arena;
    private boolean closed;

    public MinecraftGeometryUploader(GlRenderDevice renderDevice, MinecraftInteropDiagnostics diagnostics) {
        this.renderDevice = renderDevice;
        this.diagnostics = diagnostics;
        this.sequentialIndices = new SequentialIndexCache(diagnostics);
        this.arena = new TransientGeometryArena(diagnostics);
    }

    public boolean canAllocate(MeshData.DrawState drawState, MinecraftVertexLayout layout) {
        ensureOpen();
        int vertexBytes = Math.multiplyExact(drawState.vertexCount(), layout.strideBytes());
        int indexBytes = Math.multiplyExact(drawState.indexCount(),
                SequentialIndexCache.bytesPerIndex(drawState.indexType().asGLType));
        return arena.canAllocate(vertexBytes, layout.strideBytes(), indexBytes);
    }

    public void uploadAndDraw(MeshData.DrawState drawState, ByteBuffer vertices,
                              MinecraftIndexPayload indices, MinecraftVertexLayout layout) {
        ensureOpen();
        int vertexBytes = Math.multiplyExact(drawState.vertexCount(), layout.strideBytes());
        ByteBuffer vertexPayload = exactSlice(vertices, vertexBytes, "vertex");
        if (indices instanceof MinecraftIndexPayload.Sequential sequential) {
            SequentialIndexCache.Binding binding = sequentialIndices.acquire(sequential);
            TransientGeometryArena.Allocation allocation =
                    arena.uploadVertices(vertexPayload, layout.strideBytes());
            VertexArray vertexArray = vertexArray(layout);
            vertexArray.bind();
            vertexArray.bindElementBuffer(binding.buffer());
            int baseVertex = Math.toIntExact(allocation.vertexOffsetBytes() / layout.strideBytes());
            glDrawElementsBaseVertex(
                    drawState.mode().asGLMode,
                    drawState.indexCount(),
                    binding.glType(),
                    0L,
                    baseVertex);
            renderDevice.invalidateState();
            diagnostics.recordHaikalatDraw(vertexBytes, binding.uploadedBytes());
            return;
        }


        ByteBuffer indexPayload;
        int indexType;
        int indexBytes;
        if (indices instanceof MinecraftIndexPayload.Explicit explicit) {
            indexBytes = Math.multiplyExact(explicit.indexCount(),
                    SequentialIndexCache.bytesPerIndex(explicit.glType()));
            indexPayload = exactSlice(explicit.data(), indexBytes, "index");
            indexType = explicit.glType();
        } else {
            throw new IllegalArgumentException("Unknown index payload " + indices);
        }

        TransientGeometryArena.Allocation allocation =
                arena.upload(vertexPayload, layout.strideBytes(), indexPayload);
        VertexArray vertexArray = vertexArray(layout);
        vertexArray.bind();
        vertexArray.bindElementBuffer(arena.indexBuffer());
        int baseVertex = Math.toIntExact(allocation.vertexOffsetBytes() / layout.strideBytes());
        glDrawElementsBaseVertex(
                drawState.mode().asGLMode,
                drawState.indexCount(),
                indexType,
                allocation.indexOffsetBytes(),
                baseVertex);
        renderDevice.invalidateState();
        diagnostics.recordHaikalatDraw(vertexBytes, indexBytes);
    }

    private VertexArray vertexArray(MinecraftVertexLayout layout) {
        return vertexArrays.computeIfAbsent(layout, this::createVertexArray);
    }

    private VertexArray createVertexArray(MinecraftVertexLayout layout) {
        VertexArray created = new VertexArray();
        try {
            created.bind();
            arena.vertexBuffer().bind();
            for (MinecraftVertexAttribute attribute : layout.attributes()) {
                switch (attribute.inputClass()) {
                    case FLOATING -> glVertexAttribPointer(
                            attribute.location(), attribute.componentCount(), attribute.glType(),
                            attribute.normalized(), layout.strideBytes(), attribute.offsetBytes());
                    case INTEGER -> glVertexAttribIPointer(
                            attribute.location(), attribute.componentCount(), attribute.glType(),
                            layout.strideBytes(), attribute.offsetBytes());
                    case DOUBLE -> glVertexAttribLPointer(
                            attribute.location(), attribute.componentCount(), attribute.glType(),
                            layout.strideBytes(), attribute.offsetBytes());
                }
                glEnableVertexAttribArray(attribute.location());
            }
            created.bindElementBuffer(arena.indexBuffer());
            created.unbind();
            return created;
        } catch (Throwable throwable) {
            created.close();
            throw throwable;
        }
    }

    public void releaseBindings() {
        if (!closed && !vertexArrays.isEmpty()) vertexArrays.values().iterator().next().unbind();
    }

    public void invalidateVertexFormatState() {
        vertexArrays.values().forEach(VertexArray::close);
        vertexArrays.clear();
    }
    public void endFrame() {
        if (!closed) arena.endFrame();
    }



    @Override
    public void close() {
        if (closed) return;
        sequentialIndices.close();
        vertexArrays.values().forEach(VertexArray::close);
        vertexArrays.clear();
        arena.close();
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Minecraft geometry uploader is closed");
    }

    private static ByteBuffer exactSlice(ByteBuffer source, int expectedBytes, String label) {
        ByteBuffer result = source.duplicate();
        if (expectedBytes < 0 || result.remaining() < expectedBytes) {
            throw new IllegalArgumentException(
                    label + " payload is too small: expected " + expectedBytes
                            + " bytes, found " + result.remaining());
        }
        result.limit(result.position() + expectedBytes);
        return result.slice();
    }
}
