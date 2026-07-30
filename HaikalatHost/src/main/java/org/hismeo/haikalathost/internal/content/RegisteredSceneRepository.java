package org.hismeo.haikalathost.internal.content;

import com.kaleblangley.haikalat.subsystems.resources.AssetId;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import com.kaleblangley.haikalat.subsystems.scene.SceneAssetService;
import com.kaleblangley.haikalat.subsystems.scene.SceneBuildPlan;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.api.content.HaikalatAssetDefinition;
import org.hismeo.haikalathost.api.content.HaikalatAssetKind;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronously prepares every registered scene up to Haikalat's immutable CPU build plan.
 *
 * <p>Reload invalidation is completed for all declared assets and every glTF referenced by the
 * last successful plans before any replacement decode is scheduled. This prevents shared
 * dependencies from invalidating a scene after its replacement task has already started.</p>
 *
 * <p>This repository deliberately has no upload, activation, render-device, or OpenGL API. A
 * successful plan remains a CPU-only resource preflight result. Advanced extensions own any GPU
 * activation they choose to perform. Haikalat 0.20.1's external presentation-target and camera
 * contracts are owned by
 * the runtime bridge, not this background preparation service. During a resource reload the last
 * successful plan is retained until its replacement finishes, so a later activation layer can
 * keep a last-known-good candidate.</p>
 *
 * <p>Runtime integration is intentionally limited to three lifecycle calls:</p>
 * <ol>
 *     <li>After creating the catalog, construct this repository with
 *     {@code HaikalatContentRegistry.instance().assets()} and call
 *     {@link #startInitialPreparation()}.</li>
 *     <li>At the frame boundary that accepts a prepared Minecraft resource generation, call
 *     {@link #reloadAll(long)} with that generation.</li>
 *     <li>Call {@link #close()} before dropping the catalog at host shutdown.</li>
 * </ol>
 *
 * <p>Do not call {@code SceneAssetService.pumpUploads}, {@code applyReadyScenes}, or create a
 * {@code GltfGpuAssetCache} as part of this integration.</p>
 */
public final class RegisteredSceneRepository implements AutoCloseable {
    private final Object lock = new Object();
    private final SceneAssetService sceneAssets;
    private final Map<ResourceLocation, Entry> entries;
    private final Set<AssetId> registeredAssets;
    private final AtomicBoolean closed = new AtomicBoolean();

    private boolean initialPreparationStarted;
    private long latestResourceGeneration = -1L;

    public RegisteredSceneRepository(
            ResourceCatalog catalog,
            List<HaikalatAssetDefinition> definitions
    ) {
        this(new SceneAssetService(Objects.requireNonNull(catalog, "catalog")), definitions);
    }

    /**
     * Injection seam for deterministic unit tests and a host-supplied executor.
     */
    RegisteredSceneRepository(
            SceneAssetService sceneAssets,
            List<HaikalatAssetDefinition> definitions
    ) {
        this.sceneAssets = Objects.requireNonNull(sceneAssets, "sceneAssets");
        Objects.requireNonNull(definitions, "definitions");

        Map<ResourceLocation, Entry> collected = new LinkedHashMap<>();
        Set<AssetId> declared = new LinkedHashSet<>();
        definitions.forEach(definition -> declared.add(toAssetId(definition.id())));
        definitions.stream()
                .filter(definition -> definition.kind() == HaikalatAssetKind.SCENE)
                .forEach(definition -> {
                    ResourceLocation id = definition.id();
                    Entry previous = collected.putIfAbsent(id, new Entry(id, toAssetId(id)));
                    if (previous != null) {
                        throw new IllegalArgumentException("Duplicate registered scene: " + id);
                    }
                });
        this.entries = collected;
        this.registeredAssets = Set.copyOf(declared);
    }

    /**
     * Starts the generation-zero CPU preparation exactly once.
     */
    public void startInitialPreparation() {
        synchronized (lock) {
            ensureOpen();
            if (initialPreparationStarted) {
                return;
            }
            initialPreparationStarted = true;
            latestResourceGeneration = 0L;
            entries.values().forEach(entry -> schedule(entry, 0L));
        }
    }

    /**
     * Invalidates and re-prepares all registered scenes after a full Minecraft resource reload.
     *
     * <p>Repeated or out-of-order generation notifications are ignored. The caller should invoke
     * this only at the render-frame activation boundary, after Minecraft has installed the new
     * resource manager.</p>
     */
    public void reloadAll(long resourceGeneration) {
        if (resourceGeneration < 0L) {
            throw new IllegalArgumentException("resourceGeneration must be non-negative");
        }
        synchronized (lock) {
            ensureOpen();
            if (resourceGeneration <= latestResourceGeneration) {
                return;
            }
            initialPreparationStarted = true;
            latestResourceGeneration = resourceGeneration;
            invalidateReloadClosure();
            entries.values().forEach(entry -> schedule(entry, resourceGeneration));
        }
    }

    /**
     * Returns the last successful immutable CPU plan, including while its replacement is loading
     * or after a replacement failed.
     */
    public Optional<SceneBuildPlan> preparedPlan(ResourceLocation sceneId) {
        Objects.requireNonNull(sceneId, "sceneId");
        synchronized (lock) {
            Entry entry = entries.get(sceneId);
            return entry == null ? Optional.empty() : Optional.ofNullable(entry.preparedPlan);
        }
    }

    public RepositorySnapshot snapshot() {
        synchronized (lock) {
            List<SceneSnapshot> scenes = entries.values().stream()
                    .map(Entry::snapshot)
                    .toList();
            long preparing = scenes.stream()
                    .filter(scene -> scene.state() == PreparationState.PREPARING)
                    .count();
            long ready = scenes.stream()
                    .filter(scene -> scene.state() == PreparationState.CPU_READY)
                    .count();
            long failed = scenes.stream()
                    .filter(scene -> scene.state() == PreparationState.FAILED)
                    .count();
            return new RepositorySnapshot(
                    closed.get(),
                    initialPreparationStarted,
                    latestResourceGeneration,
                    (int) preparing,
                    (int) ready,
                    (int) failed,
                    scenes);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (lock) {
            entries.values().forEach(entry -> {
                entry.requestSequence++;
                entry.state = PreparationState.CLOSED;
                entry.preparedPlan = null;
                entry.preparedResourceGeneration = -1L;
            });
        }
        sceneAssets.close();
    }

    private void invalidateReloadClosure() {
        Set<AssetId> changedAssets = new LinkedHashSet<>(registeredAssets);
        entries.values().stream()
                .map(entry -> entry.preparedPlan)
                .filter(Objects::nonNull)
                .flatMap(plan -> plan.gltfAssets().keySet().stream())
                .map(SceneBuildPlan.AssetVariant::asset)
                .forEach(changedAssets::add);
        entries.values().stream()
                .map(entry -> entry.assetId)
                .forEach(changedAssets::add);
        changedAssets.forEach(sceneAssets::invalidate);
    }

    private void schedule(Entry entry, long resourceGeneration) {
        long requestSequence = ++entry.requestSequence;
        entry.state = PreparationState.PREPARING;
        entry.resourceGeneration = resourceGeneration;
        entry.failure = null;

        try {
            sceneAssets.loadPlan(entry.assetId).whenComplete((plan, failure) ->
                    complete(entry, requestSequence, plan, failure));
        } catch (RuntimeException failure) {
            complete(entry, requestSequence, null, failure);
        }
    }

    private void complete(
            Entry entry,
            long requestSequence,
            SceneBuildPlan plan,
            Throwable failure
    ) {
        synchronized (lock) {
            if (closed.get() || entry.requestSequence != requestSequence) {
                return;
            }
            if (failure == null) {
                entry.preparedPlan = Objects.requireNonNull(plan, "plan");
                entry.preparedResourceGeneration = entry.resourceGeneration;
                entry.state = PreparationState.CPU_READY;
                entry.failure = null;
                return;
            }

            Throwable cause = unwrap(failure);
            entry.state = PreparationState.FAILED;
            entry.failure = FailureSnapshot.capture(cause);
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Registered scene repository is closed");
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = Objects.requireNonNull(failure, "failure");
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static AssetId toAssetId(ResourceLocation id) {
        return AssetId.of(id.getNamespace(), id.getPath());
    }

    public enum PreparationState {
        REGISTERED,
        PREPARING,
        CPU_READY,
        FAILED,
        CLOSED
    }

    public record FailureSnapshot(
            String phase,
            String asset,
            String exceptionType,
            String message
    ) {
        public FailureSnapshot {
            exceptionType = Objects.requireNonNull(exceptionType, "exceptionType");
            message = Objects.requireNonNull(message, "message");
        }

        private static FailureSnapshot capture(Throwable failure) {
            if (failure instanceof SceneAssetService.SceneLoadException sceneFailure) {
                Throwable cause = sceneFailure.getCause();
                Throwable reported = cause == null ? sceneFailure : cause;
                return new FailureSnapshot(
                        sceneFailure.phase(),
                        sceneFailure.asset().toString(),
                        reported.getClass().getName(),
                        messageOf(reported));
            }
            return new FailureSnapshot(
                    null,
                    null,
                    failure.getClass().getName(),
                    messageOf(failure));
        }

        private static String messageOf(Throwable failure) {
            String message = failure.getMessage();
            return message == null || message.isBlank()
                    ? failure.getClass().getSimpleName()
                    : message;
        }
    }

    public record SceneSnapshot(
            ResourceLocation id,
            PreparationState state,
            long resourceGeneration,
            long requestSequence,
            boolean hasPreparedPlan,
            long preparedResourceGeneration,
            long preparedSceneGeneration,
            int nodeCount,
            int gltfAssetCount,
            FailureSnapshot failure
    ) {
        public SceneSnapshot {
            id = Objects.requireNonNull(id, "id");
            state = Objects.requireNonNull(state, "state");
        }
    }

    public record RepositorySnapshot(
            boolean closed,
            boolean initialPreparationStarted,
            long resourceGeneration,
            int preparingCount,
            int readyCount,
            int failedCount,
            List<SceneSnapshot> scenes
    ) {
        public RepositorySnapshot {
            scenes = List.copyOf(Objects.requireNonNull(scenes, "scenes"));
        }
    }

    private static final class Entry {
        private final ResourceLocation id;
        private final AssetId assetId;

        private PreparationState state = PreparationState.REGISTERED;
        private long resourceGeneration = -1L;
        private long requestSequence;
        private SceneBuildPlan preparedPlan;
        private long preparedResourceGeneration = -1L;
        private FailureSnapshot failure;

        private Entry(ResourceLocation id, AssetId assetId) {
            this.id = id;
            this.assetId = assetId;
        }

        private SceneSnapshot snapshot() {
            SceneBuildPlan plan = preparedPlan;
            return new SceneSnapshot(
                    id,
                    state,
                    resourceGeneration,
                    requestSequence,
                    plan != null,
                    preparedResourceGeneration,
                    plan == null ? -1L : plan.generation().value(),
                    plan == null ? 0 : plan.definition().nodes().size(),
                    plan == null ? 0 : plan.gltfAssets().size(),
                    failure);
        }
    }
}
