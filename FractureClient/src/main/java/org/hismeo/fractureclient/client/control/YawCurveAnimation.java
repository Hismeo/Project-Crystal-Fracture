package org.hismeo.fractureclient.client.control;

import com.kaleblangley.haikalat.core.curve.Curve1f;

import java.util.Objects;

/** Time-based shortest-path yaw animation backed by a Haikalat easing curve. */
final class YawCurveAnimation {
    private static final float ANGLE_EPSILON = 0.001F;

    private final Curve1f curve;
    private boolean initialised;
    private boolean animating;
    private float currentYaw;
    private float startYaw;
    private float targetYaw;
    private long startedNanos;
    private long durationNanos;

    YawCurveAnimation(Curve1f curve) {
        this.curve = Objects.requireNonNull(curve, "curve");
    }

    boolean isInitialised() {
        return initialised;
    }

    float targetYaw() {
        if (!initialised) {
            throw new IllegalStateException("yaw animation is not initialised");
        }
        return targetYaw;
    }

    void snap(float yaw, long nowNanos) {
        float value = wrapDegrees(yaw);
        initialised = true;
        animating = false;
        currentYaw = value;
        startYaw = value;
        targetYaw = value;
        startedNanos = nowNanos;
        durationNanos = 0L;
    }

    void retarget(float yaw, float durationSeconds, long nowNanos) {
        if (!Float.isFinite(durationSeconds) || durationSeconds < 0.0F) {
            throw new IllegalArgumentException("durationSeconds must be finite and non-negative");
        }
        if (!initialised) {
            snap(yaw, nowNanos);
            return;
        }

        float sampledYaw = sample(nowNanos);
        float nextTarget = wrapDegrees(yaw);
        float delta = wrapDegrees(nextTarget - sampledYaw);
        if (durationSeconds == 0.0F || Math.abs(delta) <= ANGLE_EPSILON) {
            snap(nextTarget, nowNanos);
            return;
        }

        startYaw = sampledYaw;
        targetYaw = wrapDegrees(sampledYaw + delta);
        startedNanos = nowNanos;
        durationNanos = Math.max(1L, (long)(durationSeconds * 1_000_000_000.0));
        animating = true;
    }

    float sample(long nowNanos) {
        if (!initialised) {
            throw new IllegalStateException("yaw animation is not initialised");
        }
        if (!animating) {
            return currentYaw;
        }

        long elapsedNanos = Math.max(0L, nowNanos - startedNanos);
        float progress = Math.min(1.0F, elapsedNanos / (float)durationNanos);
        float eased = curve.sample(progress);
        if (!Float.isFinite(eased)) {
            throw new IllegalStateException("yaw curve produced a non-finite value");
        }
        eased = Math.max(0.0F, Math.min(1.0F, eased));
        currentYaw = wrapDegrees(startYaw + wrapDegrees(targetYaw - startYaw) * eased);
        if (progress >= 1.0F) {
            currentYaw = targetYaw;
            animating = false;
        }
        return currentYaw;
    }

    void reset() {
        initialised = false;
        animating = false;
        currentYaw = 0.0F;
        startYaw = 0.0F;
        targetYaw = 0.0F;
        startedNanos = 0L;
        durationNanos = 0L;
    }

    static float angleDelta(float first, float second) {
        return wrapDegrees(first - second);
    }

    static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
