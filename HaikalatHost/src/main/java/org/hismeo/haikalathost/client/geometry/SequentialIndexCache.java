package org.hismeo.haikalathost.client.geometry;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;

public final class SequentialIndexCache implements AutoCloseable {
    private final Map<Key, Entry> entries = new HashMap<>();
    private final MinecraftInteropDiagnostics diagnostics;

    public SequentialIndexCache(MinecraftInteropDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Binding acquire(MinecraftIndexPayload.Sequential payload) {
        Key key = new Key(payload.mode(), payload.glType());
        Entry entry = entries.computeIfAbsent(
                key, ignored -> new Entry(GlBuffer.elementArrayBuffer(GL_DYNAMIC_DRAW)));
        if (entry.indexCapacity >= payload.indexCount()) {
            if (payload.indexCount() == 0) {
                return new Binding(entry.buffer, payload.glType(), 0, ByteBuffer.allocateDirect(0));
            }

            return new Binding(entry.buffer, payload.glType(), 0,
                    slice(entry.cpuData, payload.indexCount(), payload.glType()));
        }

        int grownCount = grownIndexCount(payload.mode(), payload.indexCount(), entry.indexCapacity);
        ByteBuffer data = generate(payload.mode(), grownCount, payload.glType());
        entry.buffer.upload(data.duplicate());
        entry.cpuData = data.asReadOnlyBuffer();
        entry.indexCapacity = grownCount;
        diagnostics.recordSequentialIndices(grownCount);
        return new Binding(entry.buffer, payload.glType(), data.remaining(),
                slice(entry.cpuData, payload.indexCount(), payload.glType()));
    }

    private static int grownIndexCount(VertexFormat.Mode mode, int requested, int current) {
        int grown = Math.max(requested, current == 0 ? requested : Math.multiplyExact(current, 2));
        if (mode == VertexFormat.Mode.QUADS || mode == VertexFormat.Mode.LINES) {
            grown = Math.multiplyExact((grown + 5) / 6, 6);
        }
        return grown;
    }

    private static ByteBuffer generate(VertexFormat.Mode mode, int indexCount, int glType) {
        int bytesPerIndex = bytesPerIndex(glType);
        int maxIndex = switch (mode) {
            case QUADS, LINES -> indexCount / 6 * 4 - 1;
            default -> indexCount - 1;
        };
        if (glType == GL_UNSIGNED_SHORT && maxIndex > 0xFFFF) {
            throw new IllegalArgumentException("Sequential short index buffer exceeds 65535 vertices");
        }

        ByteBuffer result = ByteBuffer.allocateDirect(Math.multiplyExact(indexCount, bytesPerIndex))
                .order(ByteOrder.nativeOrder());
        if (mode == VertexFormat.Mode.QUADS || mode == VertexFormat.Mode.LINES) {
            for (int written = 0, base = 0; written < indexCount; written += 6, base += 4) {
                if (mode == VertexFormat.Mode.QUADS) {
                    put(result, glType, base);
                    put(result, glType, base + 1);
                    put(result, glType, base + 2);
                    put(result, glType, base + 2);
                    put(result, glType, base + 3);
                    put(result, glType, base);
                } else {
                    put(result, glType, base);
                    put(result, glType, base + 1);
                    put(result, glType, base + 2);
                    put(result, glType, base + 3);
                    put(result, glType, base + 2);
                    put(result, glType, base + 1);
                }
            }
        } else {
            for (int index = 0; index < indexCount; index++) {
                put(result, glType, index);
            }
        }
        return result.flip();
    }

    private static void put(ByteBuffer target, int glType, int value) {
        if (glType == GL_UNSIGNED_SHORT) {
            target.putShort((short) value);
        } else if (glType == GL_UNSIGNED_INT) {
            target.putInt(value);
        } else {
            throw new IllegalArgumentException("Unsupported index type: " + glType);
        }
    }

    private static ByteBuffer slice(ByteBuffer data, int indexCount, int glType) {
        int bytes = Math.multiplyExact(indexCount, bytesPerIndex(glType));
        ByteBuffer result = data.duplicate();
        result.position(0);
        result.limit(bytes);
        return result.slice();
    }

    public static int bytesPerIndex(int glType) {
        return switch (glType) {
            case GL_UNSIGNED_SHORT -> Short.BYTES;
            case GL_UNSIGNED_INT -> Integer.BYTES;
            default -> throw new IllegalArgumentException("Unsupported index type: " + glType);
        };
    }

    @Override
    public void close() {
        entries.values().forEach(entry -> entry.buffer.close());
        entries.clear();
    }

    private record Key(VertexFormat.Mode mode, int glType) {
    }

    private static final class Entry {
        private final GlBuffer buffer;
        private int indexCapacity;
        private ByteBuffer cpuData;

        private Entry(GlBuffer buffer) {
            this.buffer = buffer;
        }
    }

    public record Binding(GlBuffer buffer, int glType, int uploadedBytes, ByteBuffer data) {
    }
}
