package org.hismeo.actionguide.api.cue;

import java.util.Objects;

public record RootMotionContract(boolean enabled, String bone, RootMotionMode mode) {
    public RootMotionContract {
        bone = bone == null ? "" : bone.trim();
        Objects.requireNonNull(mode, "root motion mode");
        if (enabled && bone.isEmpty()) {
            throw new IllegalArgumentException("root motion bone is required when enabled");
        }
    }
}
