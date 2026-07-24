package org.hismeo.haikalathost.client.extraction;

import java.util.Arrays;

public record CameraSnapshot(
        double x,
        double y,
        double z,
        float[] viewMatrix,
        float[] projectionMatrix,
        float fieldOfViewDegrees
) {
    public CameraSnapshot {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("camera position must be finite");
        }
        viewMatrix = copyMatrix(viewMatrix, "viewMatrix");
        projectionMatrix = copyMatrix(projectionMatrix, "projectionMatrix");
        if (!Float.isFinite(fieldOfViewDegrees) || fieldOfViewDegrees <= 0.0f) {
            throw new IllegalArgumentException("field of view must be finite and positive");
        }
    }

    @Override public float[] viewMatrix() { return viewMatrix.clone(); }
    @Override public float[] projectionMatrix() { return projectionMatrix.clone(); }

    private static float[] copyMatrix(float[] matrix, String label) {
        if (matrix == null || matrix.length != 16) {
            throw new IllegalArgumentException(label + " must contain 16 values");
        }
        float[] result = matrix.clone();
        if (Arrays.stream(toDouble(result)).anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException(label + " must contain finite values");
        }
        return result;
    }

    private static double[] toDouble(float[] values) {
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) result[index] = values[index];
        return result;
    }
}
