package org.hismeo.haikalathost.client.geometry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/** Copies supported Minecraft layouts directly into a mapped canonical arena allocation. */
public final class CanonicalVertexEncoder {
    private static final int WHITE_RGBA = 0xFFFFFFFF;
    private static final short FULL_BRIGHT = 240;
    private static final byte UP_NORMAL = 127;

    private CanonicalVertexEncoder() {
    }

    public static void encode(
            ByteBuffer source,
            CanonicalSourceLayout layout,
            int vertexCount,
            ByteBuffer destination
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(destination, "destination");
        if (vertexCount < 0) throw new IllegalArgumentException("vertexCount must not be negative");
        int sourceBytes = Math.multiplyExact(vertexCount, layout.strideBytes());
        int destinationBytes = Math.multiplyExact(vertexCount, CanonicalVertexLayout.STRIDE_BYTES);
        if (source.remaining() < sourceBytes) {
            throw new IllegalArgumentException(
                    "source has fewer bytes than the declared vertex count");
        }
        if (destination.remaining() < destinationBytes) {
            throw new IllegalArgumentException(
                    "destination has fewer bytes than the canonical vertex payload");
        }

        ByteBuffer input = source.duplicate().order(ByteOrder.nativeOrder());
        ByteBuffer output = destination.order(ByteOrder.nativeOrder());
        int sourceBase = input.position();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int base = sourceBase + vertex * layout.strideBytes();
            copyPosition(input, base + layout.positionOffset(), output);
            copyColor(input, base, layout.colorOffset(), output);
            copyUv(input, base, layout.uvOffset(), output);
            copyPackedPair(input, base, layout.overlayOffset(), (short) 0, output);
            copyPackedPair(input, base, layout.lightOffset(), FULL_BRIGHT, output);
            copyNormal(input, base, layout.normalOffset(), output);
        }
    }

    private static void copyPosition(ByteBuffer source, int offset, ByteBuffer target) {
        target.putFloat(source.getFloat(offset));
        target.putFloat(source.getFloat(offset + Float.BYTES));
        target.putFloat(source.getFloat(offset + 2 * Float.BYTES));
    }

    private static void copyColor(
            ByteBuffer source, int base, int offset, ByteBuffer target) {
        target.putInt(offset == -1 ? WHITE_RGBA : source.getInt(base + offset));
    }

    private static void copyUv(
            ByteBuffer source, int base, int offset, ByteBuffer target) {
        if (offset == -1) {
            target.putFloat(0.0F).putFloat(0.0F);
        } else {
            target.putFloat(source.getFloat(base + offset));
            target.putFloat(source.getFloat(base + offset + Float.BYTES));
        }
    }

    private static void copyPackedPair(
            ByteBuffer source, int base, int offset, short fallback, ByteBuffer target) {
        if (offset == -1) {
            target.putShort(fallback).putShort(fallback);
        } else {
            target.putShort(source.getShort(base + offset));
            target.putShort(source.getShort(base + offset + Short.BYTES));
        }
    }

    private static void copyNormal(
            ByteBuffer source, int base, int offset, ByteBuffer target) {
        if (offset == -1) {
            target.put((byte) 0).put(UP_NORMAL).put((byte) 0);
        } else {
            target.put(source.get(base + offset));
            target.put(source.get(base + offset + 1));
            target.put(source.get(base + offset + 2));
        }
        target.put((byte) 0);
    }
}
