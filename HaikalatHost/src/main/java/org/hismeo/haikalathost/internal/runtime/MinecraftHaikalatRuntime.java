package org.hismeo.haikalathost.internal.runtime;

import com.kaleblangley.haikalat.backend.GlCapabilityContract;
import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.api.runtime.HostCapabilities;
import org.hismeo.haikalathost.api.runtime.HostLifecycleState;
import org.hismeo.haikalathost.api.runtime.HostStatus;
import org.hismeo.haikalathost.internal.content.HaikalatContentRegistry;
import org.hismeo.haikalathost.internal.content.RegisteredSceneRepository;
import org.hismeo.haikalathost.internal.diagnostics.HostVersionSnapshot;
import org.hismeo.haikalathost.internal.extension.HaikalatExtensionContexts;
import org.hismeo.haikalathost.internal.extension.HaikalatExtensionRegistry;
import org.hismeo.haikalathost.internal.extension.HaikalatExtensionScheduler;
import org.hismeo.haikalathost.internal.interop.GlStateFootprint;
import org.hismeo.haikalathost.internal.interop.MinecraftGlInteropScope;
import org.hismeo.haikalathost.internal.resource.PreparedReloadInbox;
import org.hismeo.haikalathost.internal.render.MinecraftCameraDescriptor;
import org.hismeo.haikalathost.internal.render.HostDebugRenderProbe;
import org.hismeo.haikalathost.internal.render.MinecraftEmbeddedRenderBridge;
import org.hismeo.haikalathost.internal.render.MinecraftPresentationTargetAdapter;
import org.hismeo.haikalathost.internal.render.MinecraftRenderTargetDescriptor;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the game-layer Haikalat device and all render-thread lifecycle transitions.
 */
public final class MinecraftHaikalatRuntime {
    private static final MinecraftHaikalatRuntime INSTANCE = new MinecraftHaikalatRuntime();

    private final AtomicReference<HostStatus> status =
            new AtomicReference<>(HostStatus.notStarted());
    private final PreparedReloadInbox reloadInbox = new PreparedReloadInbox();
    private final WorldScopeCoordinator<ClientLevel> worlds = new WorldScopeCoordinator<>();
    private final HaikalatExtensionRegistry extensionRegistry =
            HaikalatExtensionRegistry.instance();

    private volatile GlRenderDevice renderDevice;
    private volatile ResourceCatalog resourceCatalog;
    private volatile RegisteredSceneRepository sceneRepository;
    private volatile MinecraftRenderTargetDescriptor renderTarget;
    private volatile MinecraftPresentationTargetAdapter.TargetSnapshot presentationTarget;
    private volatile MinecraftCameraDescriptor camera;
    private volatile ExternalCamera externalCamera;
    private volatile MinecraftEmbeddedRenderBridge embeddedRenderer;
    private volatile HaikalatExtensionScheduler extensionScheduler;
    private final MinecraftPresentationTargetAdapter presentationTargets =
            new MinecraftPresentationTargetAdapter();
    private long frameIndex;
    private long cameraRevision;
    private long extensionResourceGeneration;
    private long lastLoggedPresentationGeneration = -1L;
    private String lastCameraMappingFailure;
    private String lastTargetMappingFailure;

    private MinecraftHaikalatRuntime() {
    }

    public static MinecraftHaikalatRuntime instance() {
        return INSTANCE;
    }

    public HostStatus status() {
        return status.get();
    }

    /**
     * Called at the outer frame boundary, before Minecraft's GameRenderer starts the frame.
     */
    public void onFrameStart() {
        RenderSystem.assertOnRenderThread();
        if (status.get().state() == HostLifecycleState.NOT_STARTED) {
            initialize();
        }
        if (!status.get().available()) {
            return;
        }

        applyQueuedLifecycleChanges();
        renderDevice.invalidateState();
        capturePresentationTarget();
        externalCamera = null;
        frameIndex++;
    }

    /**
     * Captures matrices while they are still stable, then presents after the level is complete.
     *
     * <p>NeoForge exposes the same mutable matrix instances to every stage, and Minecraft may
     * modify the projection later in the render. Copying only at {@code AFTER_LEVEL} is therefore
     * too late on some render paths.</p>
     */
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!status.get().available()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            captureCamera(event);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        // Other render integrations may replace Minecraft's target after RenderFrame.Pre.
        capturePresentationTarget();
        MinecraftEmbeddedRenderBridge renderer = embeddedRenderer;
        MinecraftPresentationTargetAdapter.TargetSnapshot target = presentationTarget;
        MinecraftCameraDescriptor capturedCamera = camera;
        ExternalCamera capturedExternalCamera = externalCamera;
        externalCamera = null;
        if (renderer != null
                && target != null
                && capturedCamera != null
                && capturedExternalCamera != null) {
            try {
                renderer.renderProbe(
                        capturedExternalCamera,
                        target,
                        capturedCamera.deltaSeconds());
                lastCameraMappingFailure = null;
            } catch (RuntimeException failure) {
                reportCameraMappingFailure(failure);
            }
            HaikalatExtensionScheduler scheduler = extensionScheduler;
            if (scheduler != null && target.renderable()) {
                scheduler.render(
                        HaikalatExtensionContexts.frame(
                                renderDevice,
                                resourceCatalog,
                                extensionResourceGeneration,
                                capturedExternalCamera,
                                target.target(),
                                capturedCamera.deltaSeconds(),
                                frameIndex),
                        this::invokeExtensionCallback);
            }
        }
    }

    public void onFrameEnd() {
        RenderSystem.assertOnRenderThread();
        GlRenderDevice currentDevice = renderDevice;
        if (currentDevice != null) {
            if (HostDebugRenderProbe.enabled()) {
                HostDebugRenderProbe.render(currentDevice, renderTarget);
            }
            currentDevice.invalidateState();
        }
    }

    /**
     * Marks the beginning of a resource reload without performing GL work on the reload worker.
     */
    public long beginResourceReload() {
        // Do not leave READY here: if Minecraft's wider reload fails, this listener's apply
        // callback is never invoked and there is no failure callback with which to undo the state.
        // RELOADING begins only after apply enqueues a generation and the Render Thread accepts it.
        return reloadInbox.begin();
    }

    /**
     * Enqueues the prepared generation; activation is deferred to the next render-frame boundary.
     */
    public void enqueuePreparedReload(long generation) {
        reloadInbox.enqueue(generation);
    }

    public void resourceReloadFailed(long generation, Throwable failure) {
        HaikalatHost.LOGGER.error(
                "HaikalatHost resource reload generation {} failed; keeping generation {}",
                generation,
                status.get().resourceGeneration(),
                failure);
        status.updateAndGet(current -> {
            if (current.state() != HostLifecycleState.RELOADING) {
                return current;
            }
            return new HostStatus(
                    HostLifecycleState.READY,
                    "resource_reload_failed",
                    "Resource reload failed; the previous generation remains active",
                    current.capabilities(),
                    current.resourceGeneration());
        });
    }

    /**
     * Level events are not guaranteed to run on the Render Thread, so only queue the desired state.
     */
    public void requestWorldLoad(ClientLevel level) {
        worlds.loaded(level);
    }

    public void requestWorldUnload(ClientLevel level) {
        worlds.unloaded(level);
    }

    public void close() {
        HostStatus current = status.get();
        if (current.state() == HostLifecycleState.CLOSED
                || current.state() == HostLifecycleState.SHUTTING_DOWN) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        status.set(new HostStatus(
                HostLifecycleState.SHUTTING_DOWN,
                "shutting_down",
                "HaikalatHost is releasing render-thread resources",
                current.capabilities(),
                current.resourceGeneration()));
        HaikalatExtensionScheduler scheduler = extensionScheduler;
        extensionScheduler = null;
        if (scheduler != null && renderDevice != null && resourceCatalog != null) {
            var context = HaikalatExtensionContexts.engine(
                    renderDevice,
                    resourceCatalog,
                    extensionResourceGeneration);
            if (worlds.active() != null) {
                scheduler.worldClosed(context, this::invokeExtensionCallback);
            }
            scheduler.close(context, this::invokeExtensionCallback);
        }
        RegisteredSceneRepository scenes = sceneRepository;
        sceneRepository = null;
        if (scenes != null) {
            try {
                scenes.close();
            } catch (RuntimeException failure) {
                HaikalatHost.LOGGER.error(
                        "Failed to close the HaikalatHost scene preparation repository",
                        failure);
            }
        }
        MinecraftEmbeddedRenderBridge renderer = embeddedRenderer;
        embeddedRenderer = null;
        if (renderer != null) {
            try {
                renderer.close();
            } catch (RuntimeException failure) {
                HaikalatHost.LOGGER.error(
                        "Failed to close the embedded Haikalat presentation runtime",
                        failure);
            }
        }
        renderDevice = null;
        resourceCatalog = null;
        renderTarget = null;
        presentationTarget = null;
        camera = null;
        externalCamera = null;
        lastCameraMappingFailure = null;
        worlds.clear();
        status.set(new HostStatus(
                HostLifecycleState.CLOSED,
                "closed",
                "HaikalatHost render-thread resources are closed",
                current.capabilities(),
                current.resourceGeneration()));
        HaikalatHost.LOGGER.info("HaikalatHost runtime closed after {} frames", frameIndex);
    }

    public long frameIndex() {
        return frameIndex;
    }

    public MinecraftRenderTargetDescriptor renderTarget() {
        return renderTarget;
    }

    public MinecraftCameraDescriptor camera() {
        return camera;
    }

    public MinecraftPresentationTargetAdapter.TargetSnapshot presentationTarget() {
        return presentationTarget;
    }

    public MinecraftEmbeddedRenderBridge.Snapshot embeddedRendererSnapshot() {
        MinecraftEmbeddedRenderBridge renderer = embeddedRenderer;
        return renderer == null ? null : renderer.snapshot();
    }

    public List<HaikalatExtensionScheduler.Snapshot> extensionSnapshots() {
        HaikalatExtensionScheduler scheduler = extensionScheduler;
        return scheduler == null ? List.of() : scheduler.snapshot();
    }

    public boolean worldPresent() {
        return worlds.active() != null;
    }

    public RegisteredSceneRepository.RepositorySnapshot sceneRepositorySnapshot() {
        RegisteredSceneRepository scenes = sceneRepository;
        return scenes == null ? null : scenes.snapshot();
    }

    GlRenderDevice renderDevice() {
        return renderDevice;
    }

    ResourceCatalog resourceCatalog() {
        return resourceCatalog;
    }

    private void initialize() {
        HaikalatContentRegistry content = HaikalatContentRegistry.instance();
        if (!content.frozen() || !extensionRegistry.frozen()) {
            status.set(new HostStatus(
                    HostLifecycleState.NOT_STARTED,
                    "content_registration_pending",
                    "Waiting for Haikalat content and extension registration",
                    HostCapabilities.unavailable(),
                    0L));
            return;
        }
        status.set(new HostStatus(
                HostLifecycleState.INITIALIZING,
                "initializing",
                "Inspecting the current Minecraft OpenGL context",
                HostCapabilities.unavailable(),
                0L));
        try {
            GlCapabilityContract.Report report = GlCapabilityContract.inspectCurrent();
            HostCapabilities capabilities = toHostCapabilities(report);
            if (!report.meetsRequirements()) {
                status.set(new HostStatus(
                        HostLifecycleState.UNAVAILABLE,
                        "capability_contract_failed",
                        report.summary(),
                        capabilities,
                        0L));
                HaikalatHost.LOGGER.error("{}", report.summary());
                return;
            }

            GlRenderDevice initializedDevice = new GlRenderDevice();
            ResourceCatalog initializedCatalog =
                    content.createResourceCatalog(extensionRegistry.namespaces());
            RegisteredSceneRepository initializedScenes =
                    new RegisteredSceneRepository(initializedCatalog, content.assets());
            initializedScenes.startInitialPreparation();
            MinecraftEmbeddedRenderBridge initializedEmbeddedRenderer =
                    new MinecraftEmbeddedRenderBridge(initializedDevice);

            renderDevice = initializedDevice;
            resourceCatalog = initializedCatalog;
            sceneRepository = initializedScenes;
            embeddedRenderer = initializedEmbeddedRenderer;
            extensionResourceGeneration = 0L;
            HaikalatExtensionScheduler initializedExtensions =
                    new HaikalatExtensionScheduler(extensionRegistry.registrations());
            extensionScheduler = initializedExtensions;
            initializedExtensions.initialize(
                    HaikalatExtensionContexts.engine(
                            initializedDevice,
                            initializedCatalog,
                            extensionResourceGeneration),
                    this::invokeExtensionCallback);
            status.set(new HostStatus(
                    HostLifecycleState.READY,
                    "ready",
                    report.summary(),
                    capabilities,
                    0L));
            HostVersionSnapshot versions = HostVersionSnapshot.capture();
            HaikalatHost.LOGGER.info(
                    "HaikalatHost versions: host={}, haikalat={}, minecraft={}, neoforge={}",
                    versions.host(),
                    versions.haikalat(),
                    versions.minecraft(),
                    versions.neoForge());
            HaikalatHost.LOGGER.info("{}", report.summary());
        } catch (RuntimeException | LinkageError failure) {
            status.set(new HostStatus(
                    HostLifecycleState.UNAVAILABLE,
                    "initialization_failed",
                    failure.getMessage() == null
                            ? failure.getClass().getSimpleName()
                            : failure.getMessage(),
                    HostCapabilities.unavailable(),
                    0L));
            HaikalatHost.LOGGER.error("Failed to initialize HaikalatHost runtime", failure);
        }
    }

    private void applyQueuedLifecycleChanges() {
        ClientLevel previousLevel = worlds.active();
        ClientLevel activeLevel = worlds.reconcile(Minecraft.getInstance().level);
        if (previousLevel != activeLevel) {
            if (previousLevel != null) {
                notifyExtensionsWorldClosed();
            }
            camera = null;
            externalCamera = null;
            lastCameraMappingFailure = null;
        }

        HostStatus current = status.get();
        var preparedGeneration = reloadInbox.newerThan(current.resourceGeneration());
        if (preparedGeneration.isEmpty()) {
            return;
        }
        long generation = preparedGeneration.getAsLong();
        if (generation > extensionResourceGeneration) {
            extensionResourceGeneration = generation;
            notifyExtensionsReloaded(generation);
        }
        RegisteredSceneRepository scenes = sceneRepository;
        if (scenes == null) {
            throw new IllegalStateException(
                    "Scene repository is unavailable while the Host runtime is ready");
        }

        scenes.reloadAll(generation);
        RegisteredSceneRepository.RepositorySnapshot snapshot = scenes.snapshot();
        if (snapshot.resourceGeneration() != generation || snapshot.preparingCount() > 0) {
            status.set(new HostStatus(
                    HostLifecycleState.RELOADING,
                    "resource_reload_preparing",
                    "Preparing " + snapshot.preparingCount()
                            + " registered scene(s) for Minecraft resource generation "
                            + generation,
                    current.capabilities(),
                    current.resourceGeneration()));
            return;
        }

        String reasonCode = snapshot.failedCount() == 0
                ? "ready"
                : "resource_reload_partial_failure";
        String message = snapshot.failedCount() == 0
                ? "Minecraft resource generation " + generation + " is CPU-ready"
                : "Minecraft resource generation " + generation + " is CPU-ready with "
                        + snapshot.failedCount()
                        + " failed scene(s); last-known-good plans were retained";
        status.set(new HostStatus(
                HostLifecycleState.READY,
                reasonCode,
                message,
                current.capabilities(),
                generation));
    }

    private void capturePresentationTarget() {
        MinecraftRenderTargetDescriptor captured = MinecraftRenderTargetDescriptor.capture(
                Minecraft.getInstance().getMainRenderTarget());
        renderTarget = captured;
        try {
            presentationTarget = presentationTargets.adapt(captured);
            lastTargetMappingFailure = null;
            if (presentationTarget.target().generation()
                    != lastLoggedPresentationGeneration) {
                lastLoggedPresentationGeneration = presentationTarget.target().generation();
                HaikalatHost.LOGGER.info(
                        "Minecraft presentation target generation {}: framebuffer={}, "
                                + "colorTexture={}, depthTexture={}, extent={}x{}, "
                                + "colorFormat={}, depthFormat={}, depthImported={}, status={}",
                        presentationTarget.target().generation(),
                        captured.framebufferId(),
                        captured.colorTextureId(),
                        captured.depthTextureId(),
                        captured.viewWidth(),
                        captured.viewHeight(),
                        hex(presentationTarget.colorInternalFormat()),
                        hex(presentationTarget.depthInternalFormat()),
                        presentationTarget.depthImported(),
                        presentationTarget.reasonCode());
            }
        } catch (RuntimeException failure) {
            presentationTarget = null;
            String message = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            if (!message.equals(lastTargetMappingFailure)) {
                lastTargetMappingFailure = message;
                HaikalatHost.LOGGER.error(
                        "Minecraft render target cannot be mapped to Haikalat 0.20.1: {}",
                        message,
                        failure);
            }
        }
    }

    private void captureCamera(RenderLevelStageEvent event) {
        try {
            MinecraftCameraDescriptor candidate = MinecraftCameraDescriptor.capture(
                    event,
                    Math.incrementExact(cameraRevision));
            // Conversion also validates matrix invertibility. Do it before AFTER_LEVEL so a bad
            // snapshot is never retained for presentation.
            ExternalCamera candidateExternalCamera = candidate.toExternalCamera();
            camera = candidate;
            externalCamera = candidateExternalCamera;
            lastCameraMappingFailure = null;
        } catch (RuntimeException failure) {
            camera = null;
            externalCamera = null;
            reportCameraMappingFailure(failure);
        }
    }

    private void reportCameraMappingFailure(RuntimeException failure) {
        String message = failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
        if (!message.equals(lastCameraMappingFailure)) {
            lastCameraMappingFailure = message;
            HaikalatHost.LOGGER.warn(
                    "Minecraft camera cannot be mapped to Haikalat 0.20.1; "
                            + "skipping embedded presentation: {}",
                    message);
            HaikalatHost.LOGGER.debug("Minecraft camera mapping failure detail", failure);
        }
    }

    private void notifyExtensionsReloaded(long generation) {
        HaikalatExtensionScheduler scheduler = extensionScheduler;
        if (scheduler == null) {
            return;
        }
        scheduler.resourcesReloaded(
                HaikalatExtensionContexts.reload(
                        renderDevice,
                        resourceCatalog,
                        generation),
                this::invokeExtensionCallback);
    }

    private void notifyExtensionsWorldClosed() {
        HaikalatExtensionScheduler scheduler = extensionScheduler;
        if (scheduler == null) {
            return;
        }
        scheduler.worldClosed(
                HaikalatExtensionContexts.engine(
                        renderDevice,
                        resourceCatalog,
                        extensionResourceGeneration),
                this::invokeExtensionCallback);
    }

    /**
     * Gives every extension callback its own complete Minecraft/Haikalat state boundary.
     */
    private void invokeExtensionCallback(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        GlRenderDevice currentDevice = Objects.requireNonNull(
                renderDevice,
                "renderDevice");
        MinecraftEmbeddedRenderBridge renderer = Objects.requireNonNull(
                embeddedRenderer,
                "embeddedRenderer");
        try (MinecraftGlInteropScope ignored =
                     MinecraftGlInteropScope.capture(GlStateFootprint.STANDARD_PIPELINE)) {
            currentDevice.invalidateState();
            renderer.executeCallback(callback);
        } finally {
            currentDevice.invalidateState();
        }
    }

    private static String hex(int value) {
        return "0x" + Integer.toHexString(value).toUpperCase(java.util.Locale.ROOT);
    }

    private static HostCapabilities toHostCapabilities(GlCapabilityContract.Report report) {
        return new HostCapabilities(
                report.contextAvailable(),
                report.majorVersion(),
                report.minorVersion(),
                report.coreProfile(),
                report.vendor(),
                report.renderer(),
                report.version(),
                report.missingRequirements().stream()
                        .map(GlCapabilityContract.Requirement::displayName)
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                report.optionalFeatures().stream()
                        .map(GlCapabilityContract.OptionalFeature::displayName)
                        .sorted(Comparator.naturalOrder())
                        .toList());
    }

}
