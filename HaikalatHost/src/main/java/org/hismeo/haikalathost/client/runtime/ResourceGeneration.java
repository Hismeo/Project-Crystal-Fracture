package org.hismeo.haikalathost.client.runtime;

import java.util.concurrent.atomic.AtomicInteger;

public final class ResourceGeneration {
    private final AtomicInteger current = new AtomicInteger(1);

    public int current() {
        return current.get();
    }

    public int advance() {
        return current.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
    }

    public boolean isCurrent(int generation) {
        return generation > 0 && current() == generation;
    }
}
