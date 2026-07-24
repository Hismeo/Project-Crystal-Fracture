package org.hismeo.haikalathost.client.geometry;

/**
 * Deterministic per-frame suballocator. GPU synchronization is supplied by the arena that owns it.
 */
public final class GpuRingAllocator {
    private final int slotBytes;
    private final int slotCount;
    private int currentSlot;
    private int cursorBytes;

    public GpuRingAllocator(int slotBytes, int slotCount) {
        if (slotBytes <= 0) throw new IllegalArgumentException("slotBytes must be positive");
        if (slotCount < 2) throw new IllegalArgumentException("slotCount must be at least two");
        Math.multiplyExact(slotBytes, slotCount);
        this.slotBytes = slotBytes;
        this.slotCount = slotCount;
    }

    public boolean canAllocate(int sizeBytes, int alignmentBytes) {
        validateRequest(sizeBytes, alignmentBytes);
        long slotBase = (long) currentSlot * slotBytes;
        long aligned = align(slotBase + cursorBytes, alignmentBytes);
        return aligned + sizeBytes <= slotBase + slotBytes;
    }

    public Allocation allocate(int sizeBytes, int alignmentBytes) {
        if (!canAllocate(sizeBytes, alignmentBytes)) {
            throw new ArenaOverflowException(
                    "Transient GPU arena slot " + currentSlot + " overflow: requested="
                            + sizeBytes + ", used=" + cursorBytes + ", capacity=" + slotBytes);
        }

        int slotBase = Math.multiplyExact(currentSlot, slotBytes);
        int absoluteOffset = Math.toIntExact(align((long) slotBase + cursorBytes, alignmentBytes));
        cursorBytes = Math.addExact(absoluteOffset - slotBase, sizeBytes);
        return new Allocation(absoluteOffset, sizeBytes, currentSlot);
    }

    public void advanceFrame() {
        currentSlot = (currentSlot + 1) % slotCount;
        cursorBytes = 0;
    }

    public int currentSlot() {
        return currentSlot;
    }

    public int usedBytes() {
        return cursorBytes;
    }

    public int slotBytes() {
        return slotBytes;
    }

    public int totalBytes() {
        return Math.multiplyExact(slotBytes, slotCount);
    }

    private static long align(long value, int alignment) {
        long remainder = value % alignment;
        return remainder == 0L ? value : Math.addExact(value, alignment - remainder);
    }

    private static void validateRequest(int sizeBytes, int alignmentBytes) {
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must not be negative");
        if (alignmentBytes <= 0) throw new IllegalArgumentException("alignmentBytes must be positive");
    }

    public record Allocation(int offsetBytes, int lengthBytes, int slot) {
    }
}
