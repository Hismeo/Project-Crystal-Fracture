package org.hismeo.haikalathost.internal.runtime;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Identity-based world lifecycle handoff that tolerates Minecraft's unload transition frame.
 */
public final class WorldScopeCoordinator<T> {
    private final ConcurrentLinkedQueue<Change<T>> changes = new ConcurrentLinkedQueue<>();
    private final Set<T> unloaded = Collections.newSetFromMap(new IdentityHashMap<>());
    private T active;

    public void loaded(T world) {
        changes.add(new Change<>(world, true));
    }

    public void unloaded(T world) {
        changes.add(new Change<>(world, false));
    }

    /**
     * Applies queued events and reconciles them with Minecraft's current world on the Render Thread.
     *
     * <p>An object that has emitted unload is not reopened while Minecraft still exposes it during
     * the transition frame.</p>
     */
    public T reconcile(T currentWorld) {
        Change<T> change;
        while ((change = changes.poll()) != null) {
            if (change.loaded()) {
                unloaded.remove(change.world());
            } else {
                unloaded.add(change.world());
                if (active == change.world()) {
                    active = null;
                }
            }
        }

        if (currentWorld == null) {
            active = null;
            unloaded.clear();
        } else if (currentWorld != active && !unloaded.contains(currentWorld)) {
            active = currentWorld;
            unloaded.removeIf(world -> world != currentWorld);
        }
        return active;
    }

    public T active() {
        return active;
    }

    public void clear() {
        active = null;
        changes.clear();
        unloaded.clear();
    }

    private record Change<T>(T world, boolean loaded) {
        private Change {
            Objects.requireNonNull(world, "world");
        }
    }
}
