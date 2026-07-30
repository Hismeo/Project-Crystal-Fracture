package org.hismeo.haikalathost.api.runtime;

/**
 * Stable, GL-free view of the HaikalatHost client runtime lifecycle.
 */
public enum HostLifecycleState {
    NOT_STARTED(false),
    INITIALIZING(false),
    READY(true),
    RELOADING(true),
    UNAVAILABLE(false),
    SHUTTING_DOWN(false),
    CLOSED(false);

    private final boolean available;

    HostLifecycleState(boolean available) {
        this.available = available;
    }

    public boolean available() {
        return available;
    }
}
