package org.hismeo.haikalathost.client.diagnostics;

import org.hismeo.haikalathost.client.gpu.FrameArenaStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FramePerformanceTrackerTest {
    @Test
    void computesPercentilesAndCarriesLatestFrameCounters() {
        FramePerformanceTracker tracker = new FramePerformanceTracker(4);
        FrameArenaStats stats = new FrameArenaStats(3L, 10L, 20L, 2, true);

        tracker.record(1_000_000L, 4, 2, FrameArenaStats.empty());
        tracker.record(2_000_000L, 5, 3, FrameArenaStats.empty());
        tracker.record(3_000_000L, 6, 4, FrameArenaStats.empty());
        tracker.record(100_000_000L, 7, 5, stats);

        FramePerformanceSnapshot snapshot = tracker.snapshot();
        assertEquals(4L, snapshot.sampleCount());
        assertEquals(4, snapshot.windowSamples());
        assertEquals(26.5, snapshot.averageCpuMillis(), 0.0001);
        assertEquals(2.0, snapshot.p50CpuMillis(), 0.0001);
        assertEquals(100.0, snapshot.p95CpuMillis(), 0.0001);
        assertEquals(100.0, snapshot.p99CpuMillis(), 0.0001);
        assertEquals(1_000.0 / 26.5, snapshot.estimatedCpuFramesPerSecond(), 0.0001);
        assertEquals(7, snapshot.lastDrawCount());
        assertEquals(5, snapshot.lastBatchCount());
        assertEquals(20L, snapshot.lastArenaOverflowBytes());
        assertEquals(2, snapshot.lastArenaOverflowPages());
        assertEquals(true, snapshot.lastBusySlotSpill());
    }

    @Test
    void evictsOldSamplesFromTheRollingWindow() {
        FramePerformanceTracker tracker = new FramePerformanceTracker(2);
        FrameArenaStats stats = FrameArenaStats.empty();
        tracker.record(1_000_000L, 0, 0, stats);
        tracker.record(2_000_000L, 0, 0, stats);
        tracker.record(3_000_000L, 0, 0, stats);
        FramePerformanceSnapshot snapshot = tracker.snapshot();
        assertEquals(3L, snapshot.sampleCount());
        assertEquals(2, snapshot.windowSamples());
        assertEquals(2.5, snapshot.averageCpuMillis(), 0.0001);
        assertEquals(2.0, snapshot.p50CpuMillis(), 0.0001);
        assertEquals(3.0, snapshot.p95CpuMillis(), 0.0001);
    }

    @Test
    void rejectsInvalidSamples() {
        FramePerformanceTracker tracker = new FramePerformanceTracker(2);
        assertThrows(IllegalArgumentException.class,
                () -> tracker.record(-1L, 0, 0, FrameArenaStats.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.record(1L, -1, 0, FrameArenaStats.empty()));
    }
}
