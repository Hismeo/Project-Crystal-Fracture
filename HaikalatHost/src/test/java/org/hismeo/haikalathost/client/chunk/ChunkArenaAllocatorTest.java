package org.hismeo.haikalathost.client.chunk;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkArenaAllocatorTest {
    @Test
    void honorsAlignmentAndCoalescesReleasedRanges() {
        ChunkArenaAllocator allocator = new ChunkArenaAllocator(128);
        ChunkArenaAllocator.Allocation first = allocator.allocate(13, 1);
        ChunkArenaAllocator.Allocation second = allocator.allocate(17, 16);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(0, first.offsetBytes());
        assertEquals(16, second.offsetBytes());
        assertEquals(30, allocator.allocatedBytes());

        allocator.release(first);
        allocator.release(second);

        assertEquals(0, allocator.allocatedBytes());
        assertEquals(128, allocator.freeBytes());
        ChunkArenaAllocator.Allocation whole = allocator.allocate(128, 1);
        assertNotNull(whole);
        assertEquals(0, whole.offsetBytes());
    }

    @Test
    void rejectsDoubleFree() {
        ChunkArenaAllocator allocator = new ChunkArenaAllocator(64);
        ChunkArenaAllocator.Allocation allocation = allocator.allocate(16, 4);
        assertNotNull(allocation);
        allocator.release(allocation);
        assertThrows(IllegalStateException.class, () -> allocator.release(allocation));
    }

    @Test
    void survivesFragmentationAndRandomReleaseOrder() {
        ChunkArenaAllocator allocator = new ChunkArenaAllocator(16 * 1024);
        List<ChunkArenaAllocator.Allocation> live = new ArrayList<>();
        Random random = new Random(0x4841494B414C4154L);

        for (int operation = 0; operation < 20_000; operation++) {
            if (!live.isEmpty() && (live.size() > 180 || random.nextInt(3) == 0)) {
                allocator.release(live.remove(random.nextInt(live.size())));
            } else {
                int length = 1 + random.nextInt(192);
                int alignment = 1 << random.nextInt(6);
                ChunkArenaAllocator.Allocation allocation = allocator.allocate(length, alignment);
                if (allocation != null) {
                    assertEquals(0, allocation.offsetBytes() % alignment);
                    live.add(allocation);
                }
            }
            assertNoOverlap(live);
            assertEquals(
                    live.stream().mapToInt(ChunkArenaAllocator.Allocation::lengthBytes).sum(),
                    allocator.allocatedBytes());
            assertTrue(allocator.allocatedBytes() <= allocator.capacityBytes());
        }

        live.forEach(allocator::release);
        assertEquals(0, allocator.allocatedBytes());
        assertEquals(allocator.capacityBytes(), allocator.freeBytes());
        assertNull(allocator.allocate(allocator.capacityBytes() + 1, 1));
    }

    private static void assertNoOverlap(List<ChunkArenaAllocator.Allocation> allocations) {
        List<ChunkArenaAllocator.Allocation> ordered = new ArrayList<>(allocations);
        ordered.sort(Comparator.comparingInt(ChunkArenaAllocator.Allocation::offsetBytes));
        for (int index = 1; index < ordered.size(); index++) {
            ChunkArenaAllocator.Allocation previous = ordered.get(index - 1);
            ChunkArenaAllocator.Allocation current = ordered.get(index);
            assertTrue(previous.offsetBytes() + previous.lengthBytes() <= current.offsetBytes());
        }
    }
}
