package org.hismeo.haikalathost.client.gpu;

public record FrameArenaStats(
        long frameNumber,
        long baseBytes,
        long overflowBytes,
        int overflowPages,
        boolean busySlotSpill
) {
    public static FrameArenaStats empty() {
        return new FrameArenaStats(-1L, 0L, 0L, 0, false);
    }
}
