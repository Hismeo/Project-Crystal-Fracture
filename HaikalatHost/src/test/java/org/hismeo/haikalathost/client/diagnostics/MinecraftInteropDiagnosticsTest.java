package org.hismeo.haikalathost.client.diagnostics;

import org.hismeo.haikalathost.client.routing.FallbackReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftInteropDiagnosticsTest {
    @Test
    void snapshotIncludesRecordedCounters() {
        MinecraftInteropDiagnostics diagnostics = new MinecraftInteropDiagnostics();

        diagnostics.recordCaptured();
        diagnostics.recordHaikalatDraw(128, 24);
        diagnostics.recordGeometryUpload(32, 8);
        diagnostics.recordChunkDraw();
        diagnostics.recordSequentialIndices(6);
        diagnostics.recordArenaFenceWait(250);

        MinecraftInteropDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.capturedMeshes());
        assertEquals(2, snapshot.haikalatDraws());
        assertEquals(160, snapshot.vertexBytes());
        assertEquals(32, snapshot.indexBytes());
        assertEquals(6, snapshot.generatedSequentialIndices());
        assertEquals(1, snapshot.arenaFenceWaits());
        assertEquals(250, snapshot.arenaFenceWaitNanos());
        assertEquals(FallbackReason.values().length, snapshot.fallbackReasons().size());
        snapshot.fallbackReasons().values().forEach(count -> assertEquals(0L, count));
    }
}
