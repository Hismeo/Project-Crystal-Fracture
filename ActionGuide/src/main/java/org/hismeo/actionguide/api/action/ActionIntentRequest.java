package org.hismeo.actionguide.api.action;

import java.util.Objects;

public record ActionIntentRequest(ActionIntentId intent, IntentPhase phase, long sequence, long clientTick) {
    public ActionIntentRequest {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(phase, "phase");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
    }
}
