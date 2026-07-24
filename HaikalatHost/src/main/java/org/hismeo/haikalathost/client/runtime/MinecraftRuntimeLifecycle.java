package org.hismeo.haikalathost.client.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.util.Unit;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.hismeo.haikalathost.client.chunk.ChunkMeshRegistry;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.client.backend.FullTakeoverConfiguration;
import org.hismeo.haikalathost.client.backend.HaikalatMinecraftBackend;
import org.hismeo.haikalathost.client.staticmesh.ModelPartStaticMeshCache;
import org.hismeo.haikalathost.client.staticmesh.ItemModelStaticMeshCache;
import org.hismeo.haikalathost.client.staticmesh.StaticVertexConsumerRegistry;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;
import org.hismeo.haikalathost.client.staticmesh.StaticVertexBufferRegistry;
import org.joml.Matrix4fc;

import java.util.concurrent.CompletionException;

import java.util.Set;
public final class MinecraftRuntimeLifecycle {
    private static final MinecraftInteropDiagnostics DIAGNOSTICS = new MinecraftInteropDiagnostics();
    private static MinecraftHaikalatRuntime runtime;
    private static volatile boolean resourceReloadInProgress;
    private static HaikalatMinecraftBackend takeoverBackend;
    private static boolean registered;

    private MinecraftRuntimeLifecycle() {
    }

    public static synchronized void register(IEventBus modEventBus) {
        if (registered) return;
        registered = true;
        modEventBus.addListener(MinecraftRuntimeLifecycle::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(MinecraftRuntimeLifecycle::onWorldLogout);
        NeoForge.EVENT_BUS.addListener(MinecraftRuntimeLifecycle::onEndFrame);
        NeoForge.EVENT_BUS.addListener(MinecraftRuntimeLifecycle::onBeginFrame);
    }

    public static MinecraftHaikalatRuntime runtimeForDraw() {
        RenderSystem.assertOnRenderThread();
        if (resourceReloadInProgress) return null;
        if (runtime == null) runtime = new MinecraftHaikalatRuntime(DIAGNOSTICS);
        return runtime;
    }

    public static boolean isResourceReloadInProgress() {
        return resourceReloadInProgress;
    }

    public static MinecraftInteropDiagnostics diagnostics() {
        return DIAGNOSTICS;
    }
    public static boolean uploadChunkMesh(
            ChunkMeshRegistry.Capture capture,
            int originX,
            int originY,
            int originZ,
            MeshData meshData
    ) {
        RenderSystem.assertOnRenderThread();
        HaikalatMinecraftBackend backend = takeoverBackend;
        return backend != null
                && !resourceReloadInProgress
                && backend.uploadChunkMesh(capture, originX, originY, originZ, meshData);
    }

    public static void captureChunkLayer(
            RenderType renderType,
            Iterable<?> visibleSections,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4fc modelView,
            Matrix4fc projection
    ) {
        RenderSystem.assertOnRenderThread();
        HaikalatMinecraftBackend backend = takeoverBackend;
        if (backend != null && !resourceReloadInProgress) {
            backend.captureChunkLayer(renderType, visibleSections, cameraX, cameraY, cameraZ,
                    modelView, projection);
        }
    }

    public static void retainChunkLayers(Object section, Set<RenderType> retained) {
        Set<RenderType> snapshot = Set.copyOf(retained);
        Runnable publish = () -> {
            HaikalatMinecraftBackend backend = takeoverBackend;
            if (backend != null) backend.retainChunkLayers(section, snapshot);
        };
        if (RenderSystem.isOnRenderThread()) publish.run();
        else RenderSystem.recordRenderCall(publish::run);
    }

    public static void releaseChunkSection(Object section) {
        RenderSystem.assertOnRenderThread();
        HaikalatMinecraftBackend backend = takeoverBackend;
        if (backend != null) backend.releaseChunkSection(section);
    }

    public static HaikalatMinecraftBackend takeoverBackend() {
        RenderSystem.assertOnRenderThread();
        return takeoverBackend;
    }

    public static boolean captureStaticVertexBuffer(
            VertexBuffer buffer,
            Matrix4fc modelView,
            Matrix4fc projection,
            ShaderInstance shader
    ) {
        RenderSystem.assertOnRenderThread();
        HaikalatMinecraftBackend backend = takeoverBackend;
        return backend != null
                && !resourceReloadInProgress
                && backend.captureStaticMesh(buffer, modelView, projection, shader);
    }

    public static void invalidateStaticVertexBuffer(VertexBuffer buffer) {
        RenderSystem.assertOnRenderThread();
        HaikalatMinecraftBackend backend = takeoverBackend;
        if (backend != null) backend.invalidateStaticMesh(buffer);
    }
    public static boolean captureStaticModelMesh(
            Object key,
            Matrix4fc modelView,
            Matrix4fc projection,
            RenderType renderType
    ) {
        RenderSystem.assertOnRenderThread();
        HaikalatMinecraftBackend backend = takeoverBackend;
        return backend != null
                && !resourceReloadInProgress
                && backend.captureStaticMesh(key, modelView, projection, renderType);
    }

    public static void invalidateStaticMesh(Object key) {
        RenderSystem.assertOnRenderThread();
        HaikalatMinecraftBackend backend = takeoverBackend;
        if (backend != null) backend.invalidateStaticMesh(key);
    }


    public static void releaseStaticVertexBuffer(VertexBuffer buffer) {
        RenderSystem.assertOnRenderThread();
        HaikalatMinecraftBackend backend = takeoverBackend;
        if (backend != null) backend.invalidateStaticMesh(buffer);
        StaticVertexBufferRegistry.release(buffer);
    }



    public static boolean mirrorChunkMesh(
            VertexBuffer vanillaBuffer, long generation, MeshData meshData) {
        RenderSystem.assertOnRenderThread();
        if (resourceReloadInProgress) return false;
        MinecraftHaikalatRuntime current = runtimeForDraw();
        return current != null && current.mirrorChunkMesh(vanillaBuffer, generation, meshData);
    }

    public static boolean drawChunkMesh(VertexBuffer vanillaBuffer) {
        RenderSystem.assertOnRenderThread();
        MinecraftHaikalatRuntime current = runtime;
        return current != null && current.drawChunkMesh(vanillaBuffer);
    }

    public static void releaseChunkBuffer(VertexBuffer vanillaBuffer) {
        RenderSystem.assertOnRenderThread();
        MinecraftHaikalatRuntime current = runtime;
        if (current != null) current.releaseChunkBuffer(vanillaBuffer);
    }

    private static void onBeginFrame(RenderFrameEvent.Pre event) {
        FullTakeoverConfiguration configuration = FullTakeoverConfiguration.current();
        if (!configuration.haikalatBackend()) return;
        StaticVertexConsumerRegistry.clear();
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (takeoverBackend == null) {
            takeoverBackend = new HaikalatMinecraftBackend(configuration, width, height);
            HaikalatHost.LOGGER.info("Haikalat takeover H0 initialized: {}, framebuffer={}x{}",
                    takeoverBackend.capabilities().report(configuration), width, height);
        }
        if (takeoverBackend.glAudit().frameActive()) takeoverBackend.abortFrame();
        takeoverBackend.beginFrame(width, height);
    }


    private static void onEndFrame(RenderFrameEvent.Post event) {
        HaikalatMinecraftBackend backend = takeoverBackend;
        if (backend != null && backend.glAudit().frameActive()) backend.endFrame();
        MinecraftHaikalatRuntime current = runtime;
        if (current != null) current.endFrame();
    }


    public static void close() {
        RenderSystem.assertOnRenderThread();
        ChunkMeshRegistry.clear();
        ModelPartStaticMeshCache.clear();
        ItemModelStaticMeshCache.clear();
        StaticVertexConsumerRegistry.clear();
        if (takeoverBackend != null) {
            takeoverBackend.close();
            takeoverBackend = null;
        }
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
        StaticVertexBufferRegistry.clear();
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier, resourceManager, preparationsProfiler, reloadProfiler,
                                      backgroundExecutor, gameExecutor) ->
                java.util.concurrent.CompletableFuture
                        .runAsync(MinecraftRuntimeLifecycle::beginReload, backgroundExecutor)
                        .thenCompose(unused -> barrier.wait(Unit.INSTANCE))
                        .handleAsync((unused, failure) -> {
                            finishReload();
                            if (failure != null) throw new CompletionException(failure);
                            return null;
                        }, gameExecutor));
    }

    private static void beginReload() {
        resourceReloadInProgress = true;
        MinecraftHaikalatRuntime current = runtime;
        if (current != null) current.beginResourceReload();
    }

    private static void finishReload() {
        MinecraftHaikalatRuntime current = runtime;
        if (current != null) current.endResourceReload();
        resourceReloadInProgress = false;
        HaikalatMinecraftBackend backend = takeoverBackend;
        ModelPartStaticMeshCache.clear();
        ItemModelStaticMeshCache.clear();
        if (backend != null) backend.advanceResourceGeneration();
    }

    private static void onWorldLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ChunkMeshRegistry.clear();
        HaikalatMinecraftBackend backend = takeoverBackend;
        if (backend != null) backend.invalidateChunkMeshes();
        ModelPartStaticMeshCache.clear();
        ItemModelStaticMeshCache.clear();
        MinecraftHaikalatRuntime current = runtime;
        if (current != null) current.releaseWorldResources();
    }
}
