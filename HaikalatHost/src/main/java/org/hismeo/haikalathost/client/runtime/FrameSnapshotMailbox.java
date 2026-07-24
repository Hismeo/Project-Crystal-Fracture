package org.hismeo.haikalathost.client.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Latest-wins O(1) publication; the render thread never waits for a producer. */
public final class FrameSnapshotMailbox<T> {
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicReference<Published<T>> latest = new AtomicReference<>();

    public long publish(T snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        long next = sequence.incrementAndGet();
        latest.set(new Published<>(next, snapshot));
        return next;
    }

    public Published<T> latestAfter(long consumedSequence) {
        if (consumedSequence < 0L) throw new IllegalArgumentException("consumed sequence must not be negative");
        Published<T> published = latest.get();
        return published != null && published.sequence > consumedSequence ? published : null;
    }

    public record Published<T>(long sequence, T snapshot) {
        public Published {
            if (sequence <= 0L) throw new IllegalArgumentException("sequence must be positive");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
