package org.hismeo.haikalathost.api.runtime;

import java.util.Objects;

/**
 * Immutable runtime status suitable for diagnostics and optional integrations.
 */
public record HostStatus(
        HostLifecycleState state,
        String reasonCode,
        String message,
        HostCapabilities capabilities,
        long resourceGeneration
) {
    public HostStatus {
        state = Objects.requireNonNull(state, "state");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        message = Objects.requireNonNull(message, "message");
        capabilities = Objects.requireNonNull(capabilities, "capabilities");
        if (resourceGeneration < 0L) {
            throw new IllegalArgumentException("resourceGeneration must be non-negative");
        }
    }

    public static HostStatus notStarted() {
        return new HostStatus(
                HostLifecycleState.NOT_STARTED,
                "not_started",
                "HaikalatHost has not reached the Minecraft render thread",
                HostCapabilities.unavailable(),
                0L);
    }

    public static HostStatus unavailable(String reasonCode, String message) {
        return new HostStatus(
                HostLifecycleState.UNAVAILABLE,
                reasonCode,
                message,
                HostCapabilities.unavailable(),
                0L);
    }

    public boolean available() {
        return state.available();
    }
}
