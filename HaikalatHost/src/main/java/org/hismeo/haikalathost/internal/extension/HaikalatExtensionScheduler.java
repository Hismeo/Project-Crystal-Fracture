package org.hismeo.haikalathost.internal.extension;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.api.client.advanced.HaikalatEngineContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatReloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serializes extension callbacks, isolates failures, and preserves deterministic ordering.
 */
public final class HaikalatExtensionScheduler {
    private final List<Entry> entries;
    private boolean initialized;
    private boolean closed;

    public HaikalatExtensionScheduler(
            List<HaikalatExtensionRegistry.Registration> registrations
    ) {
        Objects.requireNonNull(registrations, "registrations");
        entries = registrations.stream().map(Entry::new).toList();
    }

    public void initialize(
            HaikalatEngineContext context,
            InvocationBoundary boundary
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(boundary, "boundary");
        if (initialized) {
            throw new IllegalStateException("Haikalat extensions are already initialized");
        }
        if (closed) {
            throw new IllegalStateException("Haikalat extensions are closed");
        }
        initialized = true;
        for (Entry entry : entries) {
            entry.initializeAttempted = true;
            entry.state = State.INITIALIZING;
            entry.lastResourceGeneration = context.resourceGeneration();
            if (invoke(
                    entry,
                    "initialize",
                    boundary,
                    () -> entry.registration.extension().initialize(context))) {
                entry.state = State.ACTIVE;
                HaikalatHost.LOGGER.info(
                        "Haikalat extension {} initialized for mod {}",
                        entry.registration.id(),
                        entry.registration.ownerModId());
            }
        }
    }

    public void render(
            HaikalatFrameContext frame,
            InvocationBoundary boundary
    ) {
        requireRunning();
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(boundary, "boundary");
        for (Entry entry : entries) {
            if (entry.state != State.ACTIVE) {
                continue;
            }
            entry.lastTargetGeneration = frame.target().generation();
            entry.lastResourceGeneration = frame.resourceGeneration();
            if (invoke(
                    entry,
                    "render",
                    boundary,
                    () -> entry.registration.extension().render(frame))) {
                entry.frames = Math.incrementExact(entry.frames);
            }
        }
    }

    public void resourcesReloaded(
            HaikalatReloadContext context,
            InvocationBoundary boundary
    ) {
        requireRunning();
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(boundary, "boundary");
        for (Entry entry : entries) {
            if (entry.state != State.ACTIVE) {
                continue;
            }
            entry.lastResourceGeneration = context.resourceGeneration();
            invoke(
                    entry,
                    "resourcesReloaded",
                    boundary,
                    () -> entry.registration.extension().resourcesReloaded(context));
        }
    }

    public void worldClosed(
            HaikalatEngineContext context,
            InvocationBoundary boundary
    ) {
        requireRunning();
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(boundary, "boundary");
        for (Entry entry : entries) {
            if (entry.state != State.ACTIVE) {
                continue;
            }
            entry.lastResourceGeneration = context.resourceGeneration();
            invoke(
                    entry,
                    "worldClosed",
                    boundary,
                    () -> entry.registration.extension().worldClosed(context));
        }
    }

    /**
     * Closes initialized extensions exactly once in reverse registration order.
     */
    public void close(
            HaikalatEngineContext context,
            InvocationBoundary boundary
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(boundary, "boundary");
        if (closed) {
            return;
        }
        closed = true;
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            if (!entry.initializeAttempted) {
                entry.state = State.CLOSED;
                continue;
            }
            invoke(
                    entry,
                    "close",
                    boundary,
                    () -> entry.registration.extension().close(context));
            entry.state = State.CLOSED;
        }
    }

    public List<Snapshot> snapshot() {
        List<Snapshot> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            result.add(new Snapshot(
                    entry.registration.id(),
                    entry.registration.ownerModId(),
                    entry.state,
                    entry.frames,
                    entry.lastTargetGeneration,
                    entry.lastResourceGeneration,
                    entry.lastFailurePhase,
                    entry.lastFailure));
        }
        return List.copyOf(result);
    }

    private boolean invoke(
            Entry entry,
            String phase,
            InvocationBoundary boundary,
            Runnable callback
    ) {
        try {
            boundary.invoke(callback);
            return true;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            entry.state = State.FAILED;
            entry.lastFailurePhase = phase;
            entry.lastFailure = failureSummary(failure);
            HaikalatHost.LOGGER.error(
                    "Haikalat extension {} failed during {} and has been disabled",
                    entry.registration.id(),
                    phase,
                    failure);
            return false;
        }
    }

    private void requireRunning() {
        if (!initialized) {
            throw new IllegalStateException("Haikalat extensions are not initialized");
        }
        if (closed) {
            throw new IllegalStateException("Haikalat extensions are closed");
        }
    }

    private static String failureSummary(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    @FunctionalInterface
    public interface InvocationBoundary {
        void invoke(Runnable callback);
    }

    public enum State {
        REGISTERED,
        INITIALIZING,
        ACTIVE,
        FAILED,
        CLOSED
    }

    public record Snapshot(
            ResourceLocation id,
            String ownerModId,
            State state,
            long frames,
            long lastTargetGeneration,
            long lastResourceGeneration,
            String lastFailurePhase,
            String lastFailure
    ) {
        public Snapshot {
            id = Objects.requireNonNull(id, "id");
            ownerModId = Objects.requireNonNull(ownerModId, "ownerModId");
            state = Objects.requireNonNull(state, "state");
            if (frames < 0L || lastTargetGeneration < -1L || lastResourceGeneration < -1L) {
                throw new IllegalArgumentException("extension counters are out of range");
            }
        }
    }

    private static final class Entry {
        private final HaikalatExtensionRegistry.Registration registration;
        private State state = State.REGISTERED;
        private boolean initializeAttempted;
        private long frames;
        private long lastTargetGeneration = -1L;
        private long lastResourceGeneration = -1L;
        private String lastFailurePhase;
        private String lastFailure;

        private Entry(HaikalatExtensionRegistry.Registration registration) {
            this.registration = Objects.requireNonNull(registration, "registration");
        }
    }
}
