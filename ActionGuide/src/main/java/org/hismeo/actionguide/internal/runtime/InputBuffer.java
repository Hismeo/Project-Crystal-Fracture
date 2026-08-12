package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.action.BufferedIntent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class InputBuffer {
    private final int capacity;
    private final long lifetimeTicks;
    private final Deque<BufferedIntent> queue = new ArrayDeque<>();
    private long highestSequence = -1;

    public InputBuffer(int capacity, long lifetimeTicks) {
        if (capacity < 1) {
            throw new IllegalArgumentException("input buffer capacity must be positive");
        }
        if (lifetimeTicks < 0) {
            throw new IllegalArgumentException("input lifetime must not be negative");
        }
        this.capacity = capacity;
        this.lifetimeTicks = lifetimeTicks;
    }

    public InputOfferResult offer(ActionIntentRequest request, long serverTick) {
        expire(serverTick);
        if (request.sequence() <= highestSequence) {
            return InputOfferResult.DUPLICATE_OR_STALE;
        }
        highestSequence = request.sequence();
        if (queue.size() >= capacity) {
            return InputOfferResult.CAPACITY_REACHED;
        }
        queue.addLast(new BufferedIntent(request.intent(), request.phase(), request.sequence(), serverTick,
                Math.addExact(serverTick, lifetimeTicks)));
        return InputOfferResult.ACCEPTED;
    }

    public Optional<BufferedIntent> consumeFirst(Predicate<BufferedIntent> matcher, long serverTick) {
        expire(serverTick);
        for (BufferedIntent intent : queue) {
            if (matcher.test(intent)) {
                queue.remove(intent);
                return Optional.of(intent);
            }
        }
        return Optional.empty();
    }

    public void expire(long serverTick) {
        queue.removeIf(intent -> intent.expiresAtServerTick() < serverTick);
    }

    public List<BufferedIntent> snapshot() {
        return List.copyOf(new ArrayList<>(queue));
    }

    public void clear() {
        queue.clear();
        highestSequence = -1;
    }
}
