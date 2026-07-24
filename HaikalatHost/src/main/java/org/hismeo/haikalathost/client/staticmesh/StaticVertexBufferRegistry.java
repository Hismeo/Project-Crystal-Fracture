package org.hismeo.haikalathost.client.staticmesh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.hismeo.haikalathost.client.geometry.CanonicalVertexLayout;
import org.hismeo.haikalathost.client.geometry.MinecraftCanonicalVertexEncoder;
import org.hismeo.haikalathost.client.geometry.MinecraftSequentialIndexEncoder;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;

/** Captures long-lived CPU uploads without creating a Minecraft GL object. */
public final class StaticVertexBufferRegistry {
    private static final IdentityHashMap<Object, StaticMeshPayload> PAYLOADS =
            new IdentityHashMap<>();
    private static final MinecraftCanonicalVertexEncoder VERTICES =
            new MinecraftCanonicalVertexEncoder();

    private StaticVertexBufferRegistry() {
    }

    public static boolean capture(VertexBuffer buffer, MeshData meshData) {
        return captureMesh(buffer, meshData);
    }

    public static boolean captureMesh(Object key, MeshData meshData) {
        RenderSystem.assertOnRenderThread();
        if (key == null) throw new NullPointerException("key");
        MeshData.DrawState state = meshData.drawState();
        if (state.vertexCount() <= 0 || state.indexCount() <= 0) {
            meshData.close();
            release(key);
            return false;
        }

        int vertexBytes =
                Math.multiplyExact(state.vertexCount(), CanonicalVertexLayout.STRIDE_BYTES);
        int indexElementBytes = state.indexType().bytes;
        int indexBytes = Math.multiplyExact(state.indexCount(), indexElementBytes);
        ByteBuffer vertices = MemoryUtil.memAlloc(vertexBytes).order(ByteOrder.nativeOrder());
        ByteBuffer indices = MemoryUtil.memAlloc(indexBytes).order(ByteOrder.nativeOrder());
        boolean published = false;
        try (meshData) {
            VERTICES.encode(
                    state.format(), meshData.vertexBuffer(), state.vertexCount(), vertices);
            vertices.flip();
            ByteBuffer explicit = meshData.indexBuffer();
            if (explicit == null) {
                MinecraftSequentialIndexEncoder.encode(
                        state.mode(), state.indexCount(), state.indexType(), indices);
            } else {
                indices.put(exactSlice(explicit, indexBytes));
            }
            indices.flip();

            StaticMeshPayload replacement = new StaticMeshPayload(
                    state.format(),
                    state.mode().asGLMode,
                    state.indexCount(),
                    state.indexType().asGLType,
                    indexElementBytes,
                    vertices,
                    indices);
            StaticMeshPayload previous = PAYLOADS.put(key, replacement);
            if (previous != null) previous.close();
            published = true;
            return true;
        } finally {
            if (!published) {
                MemoryUtil.memFree(indices);
                MemoryUtil.memFree(vertices);
            }
        }
    }

    public static boolean replaceIndices(
            VertexBuffer buffer, ByteBufferBuilder.Result result) {
        RenderSystem.assertOnRenderThread();
        try (result) {
            StaticMeshPayload payload = PAYLOADS.get(buffer);
            if (payload == null) return false;
            payload.replaceIndices(result.byteBuffer());
            return true;
        }
    }

    static StaticMeshPayload get(Object key) {
        RenderSystem.assertOnRenderThread();
        return PAYLOADS.get(key);
    }

    public static void release(Object key) {
        RenderSystem.assertOnRenderThread();
        StaticMeshPayload removed = PAYLOADS.remove(key);
        if (removed != null) removed.close();
    }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        PAYLOADS.values().forEach(StaticMeshPayload::close);
        PAYLOADS.clear();
        VERTICES.clear();
    }

    public static boolean contains(Object key) {
        RenderSystem.assertOnRenderThread();
        return PAYLOADS.containsKey(key);
    }

    private static ByteBuffer exactSlice(ByteBuffer source, int bytes) {
        ByteBuffer result = source.duplicate().order(ByteOrder.nativeOrder());
        if (result.remaining() < bytes) {
            throw new IllegalArgumentException("static index payload is too small");
        }
        result.limit(result.position() + bytes);
        return result.slice().order(ByteOrder.nativeOrder());
    }
}
