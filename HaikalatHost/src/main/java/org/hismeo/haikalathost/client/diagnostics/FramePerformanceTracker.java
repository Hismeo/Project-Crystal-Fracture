package org.hismeo.haikalathost.client.diagnostics;

import org.hismeo.haikalathost.client.gpu.FrameArenaStats;

import java.util.Arrays;
import java.util.Objects;

/** Allocation-free-on-record rolling CPU frame-time tracker. */
public final class FramePerformanceTracker {
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;

    private final long[] cpuNanos;
    private int cursor;
    private int size;
    private long sampleCount;
    private long windowTotalNanos;
    private int lastDrawCount;
    private int lastBatchCount;
    private FrameArenaStats lastArenaStats = FrameArenaStats.empty();

    public FramePerformanceTracker(int windowSize) {
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be positive");
        cpuNanos = new long[windowSize];
    }

    public void record(
            long frameCpuNanos,
            int drawCount,
            int batchCount,
            FrameArenaStats arenaStats
    ) {
        if (frameCpuNanos < 0L) throw new IllegalArgumentException("frameCpuNanos must not be negative");
        if (drawCount < 0 || batchCount < 0) {
            throw new IllegalArgumentException("draw and batch counts must not be negative");
        }
        Objects.requireNonNull(arenaStats, "arenaStats");

        if (size == cpuNanos.length) {
            windowTotalNanos -= cpuNanos[cursor];
        } else {
            size++;
        }
        cpuNanos[cursor] = frameCpuNanos;
        windowTotalNanos += frameCpuNanos;
        cursor = (cursor + 1) % cpuNanos.length;
        sampleCount++;
        lastDrawCount = drawCount;
        lastBatchCount = batchCount;
        lastArenaStats = arenaStats;
    }

    public long sampleCount() {
        return sampleCount;
    }

    public FramePerformanceSnapshot snapshot() {
        if (size == 0) return FramePerformanceSnapshot.empty();
        long[] sorted = Arrays.copyOf(cpuNanos, size);
        Arrays.sort(sorted);
        double averageMillis = windowTotalNanos / (double) size / NANOS_PER_MILLISECOND;
        return new FramePerformanceSnapshot(
                sampleCount,
                size,
                averageMillis,
                percentileMillis(sorted, 0.50),
                percentileMillis(sorted, 0.95),
                percentileMillis(sorted, 0.99),
                averageMillis == 0.0 ? 0.0 : 1_000.0 / averageMillis,
                lastDrawCount,
                lastBatchCount,
                lastArenaStats.baseBytes(),
                lastArenaStats.overflowBytes(),
                lastArenaStats.overflowPages(),
                lastArenaStats.busySlotSpill());
    }

    private static double percentileMillis(long[] sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1);
        return sorted[index] / NANOS_PER_MILLISECOND;
    }
}
