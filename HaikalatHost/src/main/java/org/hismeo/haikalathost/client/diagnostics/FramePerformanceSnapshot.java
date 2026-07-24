package org.hismeo.haikalathost.client.diagnostics;

/** Immutable CPU-side frame submission statistics for the latest rolling window. */
public record FramePerformanceSnapshot(
        long sampleCount,
        int windowSamples,
        double averageCpuMillis,
        double p50CpuMillis,
        double p95CpuMillis,
        double p99CpuMillis,
        double estimatedCpuFramesPerSecond,
        int lastDrawCount,
        int lastBatchCount,
        long lastArenaBaseBytes,
        long lastArenaOverflowBytes,
        int lastArenaOverflowPages,
        boolean lastBusySlotSpill
) {
    public static FramePerformanceSnapshot empty() {
        return new FramePerformanceSnapshot(
                0L, 0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0, 0, 0L, 0L, 0, false);
    }
}
