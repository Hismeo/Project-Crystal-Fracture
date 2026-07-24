package org.hismeo.haikalathost.client.backend;

import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.chunk.ChunkMeshRegistry;
import org.hismeo.haikalathost.client.chunk.LongLivedChunkMeshManager;
import org.hismeo.haikalathost.client.diagnostics.FramePerformanceSnapshot;
import org.hismeo.haikalathost.client.extraction.DirectDrawStateTracker;
import org.hismeo.haikalathost.client.extraction.WorldRenderSnapshot;
import org.hismeo.haikalathost.client.runtime.FrameSnapshotMailbox;
import org.hismeo.haikalathost.client.runtime.ResourceGeneration;
import org.hismeo.haikalathost.client.scene.RenderScene;
import org.hismeo.haikalathost.client.submission.TakeoverFrameSubmission;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.hismeo.haikalathost.client.staticmesh.LongLivedStaticMeshManager;

import org.joml.Matrix4fc;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns the single Haikalat device and Host render-scene state for takeover mode. */
public final class HaikalatMinecraftBackend implements AutoCloseable {
    private final FullTakeoverConfiguration configuration;
    private final BackendCapabilities capabilities;
    private final GlRenderDevice renderDevice;
    private final MinecraftGlAudit glAudit;
    private final FrameSnapshotMailbox<WorldRenderSnapshot> snapshots = new FrameSnapshotMailbox<>();
    private final ResourceGeneration resourceGeneration = new ResourceGeneration();
    private final RenderScene renderScene = new RenderScene();
    private final TakeoverFrameSubmission submission;
    private final LongLivedChunkMeshManager chunkMeshes;
    private final LongLivedStaticMeshManager staticMeshes;
    private int framebufferWidth;
    private int framebufferHeight;
    private MinecraftGlAudit.Scope frameScope;
    private long consumedSnapshotSequence;
    private boolean closed;

    public HaikalatMinecraftBackend(FullTakeoverConfiguration configuration, int width, int height) {
        RenderSystem.assertOnRenderThread();
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if (!configuration.haikalatBackend()) {
            throw new IllegalArgumentException("Cannot create Haikalat backend in vanilla mode");
        }
        this.capabilities = LwjglBackendCapabilityProbe.detect();
        List<String> missing = capabilities.missingRequirements(configuration);
        if (!missing.isEmpty()) throw new BackendCapabilityException(capabilities.report(configuration));
        this.renderDevice = new GlRenderDevice();
        this.glAudit = new MinecraftGlAudit(configuration.validation());
        this.framebufferWidth = positive(width, "width");
        this.framebufferHeight = positive(height, "height");
        this.submission = new TakeoverFrameSubmission(
                renderDevice, framebufferWidth, framebufferHeight, configuration.gpuDriven());
        try {
            this.chunkMeshes = new LongLivedChunkMeshManager(MinecraftRuntimeLifecycle.diagnostics());
            this.staticMeshes = new LongLivedStaticMeshManager();
        } catch (Throwable failure) {
            submission.close();
            throw failure;
        }
    }

    public void beginFrame(int width, int height) {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        if (frameScope != null) throw new IllegalStateException("Frame already active");
        resizeIfNeeded(width, height);
        frameScope = glAudit.beginHaikalatFrame();
        try {
            DirectDrawStateTracker.clearPassOverrides();
            submission.beginFrame();
            applyLatestSnapshot();
            renderDevice.invalidateState();
        } catch (Throwable failure) {
            MinecraftGlAudit.Scope scope = frameScope;
            frameScope = null;
            try {
                scope.close();
            } catch (Throwable closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public void endFrame() {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        if (frameScope == null) throw new IllegalStateException("No active frame");
        MinecraftGlAudit.Scope scope = frameScope;
        frameScope = null;
        try {
            try {
                submission.finishFrame();
            } finally {
                try {
                    chunkMeshes.endFrame();
                } finally {
                    staticMeshes.endFrame();
                }
            }
        } finally {
            scope.close();
        }
    }

    public void abortFrame() {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        MinecraftGlAudit.Scope scope = frameScope;
        if (scope == null) return;
        frameScope = null;
        try {
            submission.abortFrame();
        } finally {
            scope.close();
        }
    }

    public void capture(RenderType renderType, MeshData meshData) {
        ensureOpen();
        submission.capture(renderType, meshData);
    }

    public void captureDirect(MeshData meshData) {
        ensureOpen();
        submission.captureDirect(
                RenderSystem.getShader(), DirectDrawStateTracker.texture(0), meshData);

    }
    public boolean captureStaticMesh(
            VertexBuffer buffer,
            Matrix4fc modelView,
            Matrix4fc projection,
            ShaderInstance shader
    ) {
        ensureOpen();
        return staticMeshes.capture(
                buffer,
                modelView,
                projection,
                shader,
                DirectDrawStateTracker.texture(0),
                submission);
    }

    public boolean captureStaticMesh(
            Object key,
            Matrix4fc modelView,
            Matrix4fc projection,
            RenderType renderType
    ) {
        ensureOpen();
        return staticMeshes.capture(
                key, modelView, projection, renderType, submission);
    }

    public void invalidateStaticMesh(Object key) {
        ensureOpen();
        staticMeshes.invalidate(key);
    }


    public boolean uploadChunkMesh(
            ChunkMeshRegistry.Capture capture,
            int originX,
            int originY,
            int originZ,
            MeshData meshData
    ) {
        ensureOpen();
        return chunkMeshes.upload(capture, originX, originY, originZ, meshData);
    }

    public void captureChunkLayer(
            RenderType renderType,
            Iterable<?> visibleSections,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4fc modelView,
            Matrix4fc projection
    ) {
        ensureOpen();
        chunkMeshes.submitLayer(renderType, visibleSections, cameraX, cameraY, cameraZ,
                modelView, projection, submission);
    }

    public void retainChunkLayers(Object section, Set<RenderType> retained) {
        ensureOpen();
        chunkMeshes.retainLayers(section, retained);
    }

    public void releaseChunkSection(Object section) {
        ensureOpen();
        chunkMeshes.releaseSection(section);
    }
    public void invalidateChunkMeshes() {
        ensureOpen();
        chunkMeshes.invalidateAll();
    }


    public long publish(WorldRenderSnapshot snapshot) {
        ensureOpen();
        return snapshots.publish(snapshot);
    }

    public int advanceResourceGeneration() {
        int generation = resourceGeneration.advance();
        submission.onResourceReload();
        DirectDrawStateTracker.clear();
        chunkMeshes.invalidateAll();
        staticMeshes.invalidateAll();
        return generation;
    }

    public int resourceGeneration() {
        return resourceGeneration.current();
    }

    public GlRenderDevice renderDevice() {
        ensureOpen();
        return renderDevice;
    }

    public BackendCapabilities capabilities() {
        return capabilities;
    }

    public RenderScene renderScene() {
        return renderScene;
    }

    public MinecraftGlAudit glAudit() {
        return glAudit;
    }

    public TakeoverFrameSubmission submission() {
        return submission;
    }

    public FramePerformanceSnapshot performanceSnapshot() {
        ensureOpen();
        return submission.performanceSnapshot();
    }

    private void applyLatestSnapshot() {
        FrameSnapshotMailbox.Published<WorldRenderSnapshot> published =
                snapshots.latestAfter(consumedSnapshotSequence);
        if (published == null) return;
        WorldRenderSnapshot snapshot = published.snapshot();
        consumedSnapshotSequence = published.sequence();
        if (!resourceGeneration.isCurrent(snapshot.resourceGeneration())) return;
        renderScene.apply(snapshot.sceneDelta());
    }

    private void resizeIfNeeded(int width, int height) {
        width = positive(width, "width");
        height = positive(height, "height");
        if (framebufferWidth == width && framebufferHeight == height) return;
        submission.resize(width, height);
        framebufferWidth = width;
        framebufferHeight = height;
        renderDevice.invalidateState();
    }

    @Override
    public void close() {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        if (frameScope != null) {
            MinecraftGlAudit.Scope scope = frameScope;
            frameScope = null;
            scope.close();
        }
        submission.close();
        renderDevice.invalidateState();
        chunkMeshes.close();
        closed = true;
        staticMeshes.close();
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Haikalat Minecraft backend is closed");
    }

    private static int positive(int value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }
}
