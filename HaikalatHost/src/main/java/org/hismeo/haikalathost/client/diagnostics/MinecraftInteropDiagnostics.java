package org.hismeo.haikalathost.client.diagnostics;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.client.routing.FallbackReason;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class MinecraftInteropDiagnostics {
    private final LongAdder capturedMeshes = new LongAdder();
    private final LongAdder haikalatDraws = new LongAdder();
    private final LongAdder vanillaFallbacks = new LongAdder();
    private final LongAdder vertexBytes = new LongAdder();
    private final LongAdder indexBytes = new LongAdder();
    private final LongAdder generatedSequentialIndices = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder arenaFenceWaits = new LongAdder();
    private final LongAdder arenaFenceWaitNanos = new LongAdder();
    private final EnumMap<FallbackReason, LongAdder> fallbackReasons = new EnumMap<>(FallbackReason.class);
    private final Set<String> reportedUnknowns = ConcurrentHashMap.newKeySet();

    public MinecraftInteropDiagnostics() {
        for (FallbackReason reason : FallbackReason.values()) {
            fallbackReasons.put(reason, new LongAdder());
        }
    }

    public void recordCaptured() {
        capturedMeshes.increment();
    }

    public void recordHaikalatDraw(long uploadedVertexBytes, long uploadedIndexBytes) {
        haikalatDraws.increment();
        recordGeometryUpload(uploadedVertexBytes, uploadedIndexBytes);
    }

    public void recordGeometryUpload(long uploadedVertexBytes, long uploadedIndexBytes) {
        vertexBytes.add(uploadedVertexBytes);
        indexBytes.add(uploadedIndexBytes);
    }

    public void recordChunkDraw() {
        haikalatDraws.increment();
    }

    public void recordChunkDraws(int count) {
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        haikalatDraws.add(count);
    }

    public void recordSequentialIndices(long count) {
        generatedSequentialIndices.add(count);
    }

    public void recordArenaFenceWait(long nanos) {
        arenaFenceWaits.increment();
        arenaFenceWaitNanos.add(nanos);
    }

    public void recordFallback(FallbackReason reason, RenderType renderType,
                               MeshData.DrawState drawState, String detail) {
        vanillaFallbacks.increment();
        fallbackReasons.get(reason).increment();

        if (reason != FallbackReason.UNSUPPORTED_VERTEX_ELEMENT
                && reason != FallbackReason.UNSUPPORTED_VERTEX_MODE
                && reason != FallbackReason.UNSUPPORTED_RENDER_TYPE) {
            return;
        }

        String structure = reason + "|" + renderType + "|" + drawState.format();
        boolean development = Boolean.getBoolean("haikalathost.developmentDiagnostics");
        if (development || reportedUnknowns.add(structure)) {
            HaikalatHost.LOGGER.warn(
                    "Minecraft draw fell back to vanilla: reason={}, renderType={}, mode={}, detail={}\n{}",
                    reason, renderType, drawState.mode(), detail, drawState.format());
        }
    }

    public void recordFailure(Throwable throwable) {
        failures.increment();
        HaikalatHost.LOGGER.error("Haikalat Minecraft interop failed and has been disabled", throwable);
    }

    public Snapshot snapshot() {
        EnumMap<FallbackReason, Long> reasons = new EnumMap<>(FallbackReason.class);
        fallbackReasons.forEach((reason, count) -> reasons.put(reason, count.sum()));
        return new Snapshot(
                capturedMeshes.sum(),
                haikalatDraws.sum(),
                vanillaFallbacks.sum(),
                vertexBytes.sum(),
                indexBytes.sum(),
                generatedSequentialIndices.sum(),
                failures.sum(),
                arenaFenceWaits.sum(),
                arenaFenceWaitNanos.sum(),
                Map.copyOf(reasons));
    }

    public void logSummary() {
        HaikalatHost.LOGGER.info("Haikalat Minecraft interop diagnostics: {}", snapshot());
    }

    public record Snapshot(
            long capturedMeshes,
            long haikalatDraws,
            long vanillaFallbacks,
            long vertexBytes,
            long indexBytes,
            long generatedSequentialIndices,
            long failures,
            long arenaFenceWaits,
            long arenaFenceWaitNanos,
            Map<FallbackReason, Long> fallbackReasons
    ) {
    }
}
