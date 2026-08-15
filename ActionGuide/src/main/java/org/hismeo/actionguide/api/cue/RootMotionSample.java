package org.hismeo.actionguide.api.cue;

/** Absolute root transform sampled from a CombatCue timeline. */
public record RootMotionSample(double x, double y, double z, double yawDegrees) {
    public static final RootMotionSample IDENTITY = new RootMotionSample(0.0, 0.0, 0.0, 0.0);

    public RootMotionSample {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("root motion sample values must be finite");
        }
    }

    static RootMotionSample from(RootMotionKeyframe keyframe) {
        return new RootMotionSample(
                keyframe.x(), keyframe.y(), keyframe.z(), keyframe.yawDegrees());
    }
}
