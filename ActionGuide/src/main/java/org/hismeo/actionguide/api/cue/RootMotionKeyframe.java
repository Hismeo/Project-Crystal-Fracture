package org.hismeo.actionguide.api.cue;

/** One server-readable sample exported from the configured CombatCue root bone. */
public record RootMotionKeyframe(CueTime time, double x, double y, double z, double yawDegrees) {
    public RootMotionKeyframe {
        java.util.Objects.requireNonNull(time, "time");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("root motion keyframe values must be finite");
        }
    }
}
