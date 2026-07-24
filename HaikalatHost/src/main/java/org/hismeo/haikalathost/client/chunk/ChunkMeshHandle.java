package org.hismeo.haikalathost.client.chunk;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import com.kaleblangley.haikalat.backend.vertex.VertexArray;
import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.mojang.blaze3d.vertex.MeshData;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;
import org.hismeo.haikalathost.client.geometry.MinecraftIndexPayload;
import org.hismeo.haikalathost.client.geometry.MinecraftVertexAttribute;
import org.hismeo.haikalathost.client.geometry.MinecraftVertexLayout;
import org.hismeo.haikalathost.client.geometry.SequentialIndexCache;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL41.glVertexAttribLPointer;

final class ChunkMeshHandle implements AutoCloseable {
    private final long generation;
    private final GlBuffer vertexBuffer;
    private final GlBuffer explicitIndexBuffer;
    private final VertexArray vertexArray;
    private final int mode;
    private final int indexCount;
    private final int indexType;
    private boolean closed;

    ChunkMeshHandle(long generation, MeshData.DrawState drawState, ByteBuffer vertices,
                    MinecraftIndexPayload indices, MinecraftVertexLayout layout,
                    SequentialIndexCache sequentialIndices,
                    MinecraftInteropDiagnostics diagnostics) {
        this.generation = generation;
        this.mode = drawState.mode().asGLMode;
        this.indexCount = drawState.indexCount();

        GlBuffer createdVertexBuffer = GlBuffer.arrayBuffer(GL_STATIC_DRAW);
        VertexArray createdVertexArray = new VertexArray();
        GlBuffer createdExplicitIndexBuffer = null;
        int resolvedIndexType;
        try {
            int vertexBytes = Math.multiplyExact(drawState.vertexCount(), layout.strideBytes());
            createdVertexBuffer.upload(exactSlice(vertices, vertexBytes, "chunk vertex"));

            GlBuffer elementBuffer;
            int uploadedIndexBytes;
            if (indices instanceof MinecraftIndexPayload.Explicit explicit) {
                createdExplicitIndexBuffer = GlBuffer.elementArrayBuffer(GL_STATIC_DRAW);
                int expected = Math.multiplyExact(explicit.indexCount(),
                        SequentialIndexCache.bytesPerIndex(explicit.glType()));
                createdExplicitIndexBuffer.upload(exactSlice(explicit.data(), expected, "chunk index"));
                elementBuffer = createdExplicitIndexBuffer;
                resolvedIndexType = explicit.glType();
                uploadedIndexBytes = expected;
            } else if (indices instanceof MinecraftIndexPayload.Sequential sequential) {
                SequentialIndexCache.Binding binding = sequentialIndices.acquire(sequential);
                elementBuffer = binding.buffer();
                resolvedIndexType = binding.glType();
                uploadedIndexBytes = binding.uploadedBytes();
            } else {
                throw new IllegalArgumentException("Unknown chunk index payload " + indices);
            }

            configure(createdVertexArray, createdVertexBuffer, layout, elementBuffer);
            diagnostics.recordGeometryUpload(vertexBytes, uploadedIndexBytes);
        } catch (Throwable throwable) {
            if (createdExplicitIndexBuffer != null) createdExplicitIndexBuffer.close();
            createdVertexArray.close();
            createdVertexBuffer.close();
            throw throwable;
        }

        this.vertexBuffer = createdVertexBuffer;
        this.vertexArray = createdVertexArray;
        this.explicitIndexBuffer = createdExplicitIndexBuffer;
        this.indexType = resolvedIndexType;
    }

    long generation() {
        return generation;
    }

    void draw(GlRenderDevice renderDevice, CommandBuffer commands) {
        if (closed) throw new IllegalStateException("Chunk mesh is closed");
        renderDevice.invalidateState();
        commands.reset();
        commands.bindVertexArray(vertexArray.id()).drawElements(mode, indexCount, indexType);
        renderDevice.execute(commands);
    }

    private static void configure(VertexArray vertexArray, GlBuffer vertexBuffer,
                                  MinecraftVertexLayout layout, GlBuffer elementBuffer) {
        vertexArray.bind();
        vertexBuffer.bind();
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
        vertexArray.bindElementBuffer(elementBuffer);
        vertexArray.unbind();
    }

    @Override
    public void close() {
        if (closed) return;
        vertexArray.close();
        if (explicitIndexBuffer != null) explicitIndexBuffer.close();
        vertexBuffer.close();
        closed = true;
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
