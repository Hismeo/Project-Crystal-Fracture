package org.hismeo.haikalathost.client.gpu;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Allocation-free-after-warmup table of unique model-view/projection pairs for one frame.
 */
public final class FrameTransformTable {
    public static final int MATRIX_FLOATS = 16;
    public static final int FLOATS_PER_TRANSFORM = MATRIX_FLOATS * 2;
    public static final int BYTES_PER_TRANSFORM = FLOATS_PER_TRANSFORM * Float.BYTES;

    private float[] values;
    private int size;

    public FrameTransformTable(int initialCapacity) {
        if (initialCapacity <= 0) throw new IllegalArgumentException("initialCapacity must be positive");
        values = new float[Math.multiplyExact(initialCapacity, FLOATS_PER_TRANSFORM)];
    }

    public int resolve(Matrix4fc modelView, Matrix4fc projection) {
        Objects.requireNonNull(modelView, "modelView");
        Objects.requireNonNull(projection, "projection");
        for (int index = 0; index < size; index++) {
            if (equalsMatrix(index, 0, modelView)
                    && equalsMatrix(index, MATRIX_FLOATS, projection)) {
                return index;
            }
        }
        ensureCapacity(size + 1);
        int created = size++;
        int offset = created * FLOATS_PER_TRANSFORM;
        modelView.get(values, offset);
        projection.get(values, offset + MATRIX_FLOATS);
        return created;
    }

    public void writeTo(ByteBuffer target) {
        Objects.requireNonNull(target, "target");
        int required = Math.multiplyExact(size, BYTES_PER_TRANSFORM);
        if (target.remaining() < required) {
            throw new IllegalArgumentException(
                    "transform target requires " + required
                            + " bytes, found " + target.remaining());
        }
        int floats = size * FLOATS_PER_TRANSFORM;
        for (int index = 0; index < floats; index++) target.putFloat(values[index]);
    }

    public Matrix4f modelView(int index, Matrix4f target) {
        return matrix(index, 0, target);
    }

    public Matrix4f projection(int index, Matrix4f target) {
        return matrix(index, MATRIX_FLOATS, target);
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
    }

    private Matrix4f matrix(int index, int matrixOffset, Matrix4f target) {
        Objects.requireNonNull(target, "target");
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return target.set(values, index * FLOATS_PER_TRANSFORM + matrixOffset);
    }

    private boolean equalsMatrix(int index, int matrixOffset, Matrix4fc matrix) {
        int offset = index * FLOATS_PER_TRANSFORM + matrixOffset;
        return same(values[offset], matrix.m00())
                && same(values[offset + 1], matrix.m01())
                && same(values[offset + 2], matrix.m02())
                && same(values[offset + 3], matrix.m03())
                && same(values[offset + 4], matrix.m10())
                && same(values[offset + 5], matrix.m11())
                && same(values[offset + 6], matrix.m12())
                && same(values[offset + 7], matrix.m13())
                && same(values[offset + 8], matrix.m20())
                && same(values[offset + 9], matrix.m21())
                && same(values[offset + 10], matrix.m22())
                && same(values[offset + 11], matrix.m23())
                && same(values[offset + 12], matrix.m30())
                && same(values[offset + 13], matrix.m31())
                && same(values[offset + 14], matrix.m32())
                && same(values[offset + 15], matrix.m33());
    }

    private void ensureCapacity(int requested) {
        int current = values.length / FLOATS_PER_TRANSFORM;
        if (requested <= current) return;
        int grown = Math.max(requested, Math.multiplyExact(current, 2));
        values = Arrays.copyOf(values, Math.multiplyExact(grown, FLOATS_PER_TRANSFORM));
    }

    private static boolean same(float left, float right) {
        return Float.floatToRawIntBits(left) == Float.floatToRawIntBits(right);
    }
}
