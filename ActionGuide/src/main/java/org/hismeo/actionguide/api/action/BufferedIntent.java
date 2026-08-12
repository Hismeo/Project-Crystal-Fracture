package org.hismeo.actionguide.api.action;

import java.util.Objects;

public record BufferedIntent(
        ActionIntentId intent,
        IntentPhase phase,
        long sequence,
        long receivedServerTick,
        long expiresAtServerTick
) {
    public BufferedIntent {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(phase, "phase");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        if (expiresAtServerTick < receivedServerTick) {
            throw new IllegalArgumentException("intent expiration precedes receipt");
        }
    }
}
