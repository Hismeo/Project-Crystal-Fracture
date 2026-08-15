package org.hismeo.actionguide.api.cue;

import java.util.Objects;

public record RootMotionContract(
        boolean enabled,
        String bone,
        RootMotionMode mode,
        java.util.List<RootMotionKeyframe> keyframes
) {
    public RootMotionContract {
        bone = bone == null ? "" : bone.trim();
        Objects.requireNonNull(mode, "root motion mode");
        keyframes = keyframes == null ? java.util.List.of() : keyframes.stream()
                .sorted(java.util.Comparator.comparing(RootMotionKeyframe::time))
                .toList();
        if (enabled && bone.isEmpty()) {
            throw new IllegalArgumentException("root motion bone is required when enabled");
        }
    }

    public RootMotionContract(boolean enabled, String bone, RootMotionMode mode) {
        this(enabled, bone, mode, java.util.List.of());
    }

    /** Samples the absolute authored root transform using linear interpolation. */
    public RootMotionSample sample(CueTime time) {
        Objects.requireNonNull(time, "time");
        if (!enabled || keyframes.isEmpty()) {
            return RootMotionSample.IDENTITY;
        }
        RootMotionKeyframe first = keyframes.getFirst();
        if (time.compareTo(first.time()) <= 0) {
            return RootMotionSample.from(first);
        }
        RootMotionKeyframe last = keyframes.getLast();
        if (time.compareTo(last.time()) >= 0) {
            return RootMotionSample.from(last);
        }
        int low = 0;
        int high = keyframes.size() - 1;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (keyframes.get(middle).time().compareTo(time) <= 0) {
                low = middle;
            } else {
                high = middle;
            }
        }
        RootMotionKeyframe left = keyframes.get(low);
        RootMotionKeyframe right = keyframes.get(high);
        double span = right.time().micros() - left.time().micros();
        double alpha = span <= 0.0 ? 0.0 : (time.micros() - left.time().micros()) / span;
        return new RootMotionSample(
                lerp(left.x(), right.x(), alpha),
                lerp(left.y(), right.y(), alpha),
                lerp(left.z(), right.z(), alpha),
                lerpAngle(left.yawDegrees(), right.yawDegrees(), alpha));
    }

    private static double lerp(double first, double second, double alpha) {
        return first + (second - first) * alpha;
    }

    private static double lerpAngle(double first, double second, double alpha) {
        double delta = ((second - first + 540.0) % 360.0) - 180.0;
        return first + delta * alpha;
    }
}
