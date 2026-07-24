package org.hismeo.haikalathost.client.gpu;

public enum FrameArenaRegion {
    VERTEX(16),
    INDEX(4),
    INSTANCE(16),
    DRAW_DATA(16),
    INDIRECT_COMMAND(4);

    private final int defaultAlignment;

    FrameArenaRegion(int defaultAlignment) {
        this.defaultAlignment = defaultAlignment;
    }

    public int defaultAlignment() {
        return defaultAlignment;
    }
}
