package org.hismeo.haikalathost.client.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkMeshRegistry {
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static final ConcurrentMap<VertexBuffer, Registration> REGISTRATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Object, Set<VertexBuffer>> SECTION_BUFFERS = new ConcurrentHashMap<>();

    private ChunkMeshRegistry() {
    }

    public static void observe(Object section, RenderType renderType, VertexBuffer vertexBuffer) {
        if (vertexBuffer == null) return;
        REGISTRATIONS.compute(vertexBuffer, (ignored, current) ->
                current == null ? new Registration(section, renderType, new AtomicLong()) : current);
        SECTION_BUFFERS.computeIfAbsent(section, ignored -> ConcurrentHashMap.newKeySet()).add(vertexBuffer);
    }

    public static Capture captureSupported(VertexBuffer vertexBuffer) {
        Registration registration = REGISTRATIONS.get(vertexBuffer);
        if (registration == null || !isSupportedLayer(registration.renderType)) return null;
        long generation = NEXT_GENERATION.incrementAndGet();
        registration.generation.set(generation);
        return new Capture(vertexBuffer, registration.section, registration.renderType, generation);
    }
    public static boolean isTracked(VertexBuffer vertexBuffer) {
        Registration registration = REGISTRATIONS.get(vertexBuffer);
        return registration != null && isSupportedLayer(registration.renderType);
    }


    private static boolean isSupportedLayer(RenderType renderType) {
        return RenderType.chunkBufferLayers().contains(renderType);
    }

    public static boolean isCurrent(VertexBuffer vertexBuffer, long generation) {
        Registration registration = REGISTRATIONS.get(vertexBuffer);
        return registration != null && registration.generation.get() == generation;
    }

    public static void clear() {
        REGISTRATIONS.clear();
        SECTION_BUFFERS.clear();
    }

    public static void releaseSection(Object section) {
        Set<VertexBuffer> buffers = SECTION_BUFFERS.remove(section);
        if (buffers == null) return;
        buffers.forEach(REGISTRATIONS::remove);
        releaseOnRenderThread(section);
    }

    private static void releaseOnRenderThread(Object section) {
        Runnable release = () -> MinecraftRuntimeLifecycle.releaseChunkSection(section);
        if (RenderSystem.isOnRenderThread()) {
            release.run();
        } else {
            RenderSystem.recordRenderCall(release::run);
        }
    }

    private record Registration(Object section, RenderType renderType, AtomicLong generation) {
    }

    public record Capture(VertexBuffer vertexBuffer, Object section,
                          RenderType renderType, long generation) {
    }
}
