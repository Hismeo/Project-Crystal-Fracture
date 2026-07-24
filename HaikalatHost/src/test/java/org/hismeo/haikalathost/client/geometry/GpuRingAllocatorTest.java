package org.hismeo.haikalathost.client.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRingAllocatorTest {
    @Test
    void alignsAllocationsAndTracksUsage() {
        GpuRingAllocator allocator = new GpuRingAllocator(64, 3);

        assertEquals(new GpuRingAllocator.Allocation(0, 3, 0), allocator.allocate(3, 1));
        assertEquals(new GpuRingAllocator.Allocation(8, 8, 0), allocator.allocate(8, 8));
        assertEquals(16, allocator.usedBytes());
        assertEquals(192, allocator.totalBytes());
    }

    @Test
    void advancesAndWrapsFrameSlots() {
        GpuRingAllocator allocator = new GpuRingAllocator(32, 2);
        allocator.allocate(12, 4);

        allocator.advanceFrame();
        assertEquals(1, allocator.currentSlot());
        assertEquals(0, allocator.usedBytes());
        assertEquals(32, allocator.allocate(4, 4).offsetBytes());

        allocator.advanceFrame();
        assertEquals(0, allocator.currentSlot());
    }

    @Test
    void rejectsOverflowAndInvalidRequests() {
        GpuRingAllocator allocator = new GpuRingAllocator(16, 2);
        allocator.allocate(12, 1);

        assertFalse(allocator.canAllocate(8, 1));
        assertThrows(ArenaOverflowException.class, () -> allocator.allocate(8, 1));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> allocator.allocate(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new GpuRingAllocator(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new GpuRingAllocator(16, 1));
        assertTrue(allocator.canAllocate(4, 1));
    }
}
