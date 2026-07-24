package org.hismeo.haikalathost.client.chunk;

import java.util.ArrayList;
import java.util.List;

/** Deterministic coalescing allocator used by each long-lived chunk arena page. */
public final class ChunkArenaAllocator {
    private final int capacityBytes;
    private final List<Range> free = new ArrayList<>();
    private int allocatedBytes;

    public ChunkArenaAllocator(int capacityBytes) {
        if (capacityBytes <= 0) throw new IllegalArgumentException("capacityBytes must be positive");
        this.capacityBytes = capacityBytes;
        free.add(new Range(0, capacityBytes));
    }

    public Allocation allocate(int lengthBytes, int alignmentBytes) {
        if (lengthBytes <= 0) throw new IllegalArgumentException("lengthBytes must be positive");
        if (alignmentBytes <= 0) throw new IllegalArgumentException("alignmentBytes must be positive");
        for (int index = 0; index < free.size(); index++) {
            Range range = free.get(index);
            int offset = align(range.offsetBytes, alignmentBytes);
            long end = (long) offset + lengthBytes;
            int rangeEnd = range.offsetBytes + range.lengthBytes;
            if (end > rangeEnd) continue;

            free.remove(index);
            if (offset > range.offsetBytes) {
                free.add(index++, new Range(range.offsetBytes, offset - range.offsetBytes));
            }
            if (end < rangeEnd) {
                free.add(index, new Range((int) end, rangeEnd - (int) end));
            }
            allocatedBytes = Math.addExact(allocatedBytes, lengthBytes);
            return new Allocation(offset, lengthBytes);
        }
        return null;
    }

    public void release(Allocation allocation) {
        if (allocation == null) return;
        int offset = allocation.offsetBytes;
        int length = allocation.lengthBytes;
        if (offset < 0 || length <= 0 || (long) offset + length > capacityBytes) {
            throw new IllegalArgumentException("allocation is outside this arena");
        }

        int insertion = 0;
        while (insertion < free.size() && free.get(insertion).offsetBytes < offset) insertion++;
        if (insertion > 0) {
            Range previous = free.get(insertion - 1);
            if (previous.offsetBytes + previous.lengthBytes > offset) {
                throw new IllegalStateException("allocation overlaps an already-free range");
            }
        }
        if (insertion < free.size()) {
            Range next = free.get(insertion);
            if (offset + length > next.offsetBytes) {
                throw new IllegalStateException("allocation overlaps an already-free range");
            }
        }

        free.add(insertion, new Range(offset, length));
        coalesceAt(insertion);
        allocatedBytes = Math.subtractExact(allocatedBytes, length);
    }

    public int capacityBytes() {
        return capacityBytes;
    }

    public int allocatedBytes() {
        return allocatedBytes;
    }

    public int freeBytes() {
        return capacityBytes - allocatedBytes;
    }

    private void coalesceAt(int index) {
        if (index > 0) {
            Range left = free.get(index - 1);
            Range current = free.get(index);
            if (left.offsetBytes + left.lengthBytes == current.offsetBytes) {
                free.set(index - 1, new Range(left.offsetBytes, left.lengthBytes + current.lengthBytes));
                free.remove(index);
                index--;
            }
        }
        if (index + 1 < free.size()) {
            Range current = free.get(index);
            Range right = free.get(index + 1);
            if (current.offsetBytes + current.lengthBytes == right.offsetBytes) {
                free.set(index, new Range(current.offsetBytes, current.lengthBytes + right.lengthBytes));
                free.remove(index + 1);
            }
        }
    }

    private static int align(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : Math.addExact(value, alignment - remainder);
    }

    public record Allocation(int offsetBytes, int lengthBytes) {
        public Allocation {
            if (offsetBytes < 0 || lengthBytes <= 0) {
                throw new IllegalArgumentException("invalid allocation");
            }
        }
    }

    private record Range(int offsetBytes, int lengthBytes) {
    }
}
