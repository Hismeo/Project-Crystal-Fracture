package org.hismeo.haikalathost.client.gpu;

public record PersistentFrameArenaConfiguration(
        int frameSlots,
        int vertexBytesPerSlot,
        int indexBytesPerSlot,
        int instanceBytesPerSlot,
        int drawDataBytesPerSlot,
        int indirectBytesPerSlot,
        int overflowPageBytes
) {
    private static final int MIB = 1024 * 1024;

    public PersistentFrameArenaConfiguration {
        if (frameSlots < 2 || frameSlots > 8) {
            throw new IllegalArgumentException("frameSlots must be in [2, 8]");
        }
        requirePositive(vertexBytesPerSlot, "vertexBytesPerSlot");
        requirePositive(indexBytesPerSlot, "indexBytesPerSlot");
        requirePositive(instanceBytesPerSlot, "instanceBytesPerSlot");
        requirePositive(drawDataBytesPerSlot, "drawDataBytesPerSlot");
        requirePositive(indirectBytesPerSlot, "indirectBytesPerSlot");
        requirePositive(overflowPageBytes, "overflowPageBytes");
        Math.multiplyExact(frameSlots, vertexBytesPerSlot);
        Math.multiplyExact(frameSlots, indexBytesPerSlot);
        Math.multiplyExact(frameSlots, instanceBytesPerSlot);
        Math.multiplyExact(frameSlots, drawDataBytesPerSlot);
        Math.multiplyExact(frameSlots, indirectBytesPerSlot);
    }

    public static PersistentFrameArenaConfiguration fromSystemProperties() {
        return new PersistentFrameArenaConfiguration(
                positiveProperty("haikalathost.frameArena.slots", 3),
                mebibytes("haikalathost.frameArena.vertexMiB", 16),
                mebibytes("haikalathost.frameArena.indexMiB", 4),
                mebibytes("haikalathost.frameArena.instanceMiB", 2),
                mebibytes("haikalathost.frameArena.drawDataMiB", 2),
                mebibytes("haikalathost.frameArena.indirectMiB", 1),
                mebibytes("haikalathost.frameArena.overflowMiB", 2));
    }

    public int bytesPerSlot(FrameArenaRegion region) {
        return switch (region) {
            case VERTEX -> vertexBytesPerSlot;
            case INDEX -> indexBytesPerSlot;
            case INSTANCE -> instanceBytesPerSlot;
            case DRAW_DATA -> drawDataBytesPerSlot;
            case INDIRECT_COMMAND -> indirectBytesPerSlot;
        };
    }

    private static int mebibytes(String property, int fallback) {
        return Math.multiplyExact(positiveProperty(property, fallback), MIB);
    }

    private static int positiveProperty(String property, int fallback) {
        int value = Integer.getInteger(property, fallback);
        requirePositive(value, property);
        return value;
    }

    private static void requirePositive(int value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
    }
}
