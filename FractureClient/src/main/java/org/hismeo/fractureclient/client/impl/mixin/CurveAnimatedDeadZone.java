package org.hismeo.fractureclient.client.impl.mixin;

import com.kaleblangley.haikalat.core.curve.Curve1f;
import com.kaleblangley.haikalat.core.curve.Curves;

/** Frame-rate-independent three-axis dead zone with separate activation and release bounds. */
final class CurveAnimatedDeadZone {
    static final double INNER_X = 3.0;
    static final double OUTER_X = 11.5;
    static final double INNER_Y = 0.1;
    static final double OUTER_Y = 6.0;
    static final double INNER_Z = 2.0;
    static final double OUTER_Z = 6.4;

    private static final double TELEPORT_DISTANCE = 40.0;
    private static final double MAX_FRAME_SECONDS = 0.10;
    private static final double MIN_TRANSITION_SECONDS = 0.05;
    private static final double MAX_TRANSITION_SECONDS = 3.0;
    private static final double SETTLE_EXPONENT = 4.605170186;
    private static final double EASE_OUT_CUBIC_POWER = 3.0;
    private static final double RELEASE_EPSILON = 0.01;
    private static final Curve1f FOLLOW_CURVE = Curves.EASE_OUT_CUBIC;

    private final Axis x = new Axis(INNER_X, OUTER_X);
    private final Axis y = new Axis(INNER_Y, OUTER_Y);
    private final Axis z = new Axis(INNER_Z, OUTER_Z);
    private boolean initialised;
    private long lastUpdateNanos;

    Position update(
            double cameraX,
            double cameraY,
            double cameraZ,
            double playerX,
            double playerY,
            double playerZ,
            float transitionSeconds,
            long nowNanos
    ) {
        if (!initialised) {
            x.snap(cameraX);
            y.snap(cameraY);
            z.snap(cameraZ);
            if (distance(playerX - cameraX, playerY - cameraY, playerZ - cameraZ)
                    > TELEPORT_DISTANCE) {
                snap(playerX, playerY, playerZ, nowNanos);
            } else {
                initialised = true;
                lastUpdateNanos = nowNanos;
            }
        }

        if (distance(playerX - x.position, playerY - y.position, playerZ - z.position)
                > TELEPORT_DISTANCE) {
            snap(playerX, playerY, playerZ, nowNanos);
            return position();
        }

        double elapsedSeconds = Math.min(
                MAX_FRAME_SECONDS,
                Math.max(0.0, (nowNanos - lastUpdateNanos) * 1.0e-9)
        );
        lastUpdateNanos = nowNanos;
        double duration = clamp(
                transitionSeconds,
                MIN_TRANSITION_SECONDS,
                MAX_TRANSITION_SECONDS
        );
        float curveStep = curveStep(elapsedSeconds, duration);
        x.update(playerX, curveStep);
        y.update(playerY, curveStep);
        z.update(playerZ, curveStep);
        return position();
    }

    void reset() {
        x.reset();
        y.reset();
        z.reset();
        initialised = false;
        lastUpdateNanos = 0L;
    }

    boolean isFollowingX() {
        return x.following;
    }

    boolean isFollowingY() {
        return y.following;
    }

    boolean isFollowingZ() {
        return z.following;
    }

    private void snap(double playerX, double playerY, double playerZ, long nowNanos) {
        x.snap(playerX);
        y.snap(playerY);
        z.snap(playerZ);
        initialised = true;
        lastUpdateNanos = nowNanos;
    }

    private Position position() {
        return new Position(x.position, y.position, z.position);
    }

    /**
     * EASE_OUT_CUBIC leaves {@code (1 - t)^3} of the distance. Feeding it this exponential
     * base step makes the remaining distance independent of the render frame rate and leaves
     * approximately one percent after the configured transition time.
     */
    private static float curveStep(double elapsedSeconds, double transitionSeconds) {
        if (elapsedSeconds <= 0.0) {
            return 0.0F;
        }
        double baseProgress = 1.0 - Math.exp(
                -SETTLE_EXPONENT * elapsedSeconds
                        / (EASE_OUT_CUBIC_POWER * transitionSeconds)
        );
        float sampled = FOLLOW_CURVE.sample((float)clamp(baseProgress, 0.0, 1.0));
        if (!Float.isFinite(sampled)) {
            throw new IllegalStateException("dead-zone curve produced a non-finite value");
        }
        return (float)clamp(sampled, 0.0, 1.0);
    }

    private static double distance(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Position(double x, double y, double z) {
    }

    private static final class Axis {
        private final double innerDistance;
        private final double outerDistance;
        private double position;
        private boolean following;

        private Axis(double innerDistance, double outerDistance) {
            if (innerDistance < 0.0 || outerDistance <= innerDistance) {
                throw new IllegalArgumentException(
                        "dead-zone outer distance must be greater than its inner distance"
                );
            }
            this.innerDistance = innerDistance;
            this.outerDistance = outerDistance;
        }

        private void update(double player, float curveStep) {
            double delta = player - position;
            double distance = Math.abs(delta);
            if (!following) {
                if (distance <= outerDistance) {
                    return;
                }
                following = true;
            }
            if (distance <= innerDistance + RELEASE_EPSILON) {
                following = false;
                return;
            }

            double target = player - Math.copySign(innerDistance, delta);
            position += (target - position) * curveStep;
            if (Math.abs(player - position) <= innerDistance + RELEASE_EPSILON) {
                following = false;
            }
        }

        private void snap(double position) {
            this.position = position;
            following = false;
        }

        private void reset() {
            position = 0.0;
            following = false;
        }
    }
}
