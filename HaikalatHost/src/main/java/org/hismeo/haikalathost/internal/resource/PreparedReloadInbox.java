package org.hismeo.haikalathost.internal.resource;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe handoff from resource reload executors to the Render Thread.
 */
public final class PreparedReloadInbox {
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong latestPrepared = new AtomicLong();

    public long begin() {
        return sequence.incrementAndGet();
    }

    public void enqueue(long generation) {
        if (generation <= 0L || generation > sequence.get()) {
            throw new IllegalArgumentException("unknown reload generation: " + generation);
        }
        latestPrepared.accumulateAndGet(generation, Math::max);
    }

    public OptionalLong newerThan(long activeGeneration) {
        if (activeGeneration < 0L) {
            throw new IllegalArgumentException("activeGeneration must be non-negative");
        }
        long candidate = latestPrepared.get();
        return candidate > activeGeneration
                ? OptionalLong.of(candidate)
                : OptionalLong.empty();
    }
}
