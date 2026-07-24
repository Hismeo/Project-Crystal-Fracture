package org.hismeo.haikalathost.client.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRingAllocatorStressTest {
    @Test
    void repeatedFramesStayAlignedAndInsideTheirSlots() {
        int slotBytes = 256 * 1024;
        int slotCount = 3;
        GpuRingAllocator allocator = new GpuRingAllocator(slotBytes, slotCount);

        for (int frame = 0; frame < 300; frame++) {
            int expectedSlot = frame % slotCount;
            int previousEnd = expectedSlot * slotBytes;
            for (int draw = 0; draw < 512; draw++) {
                int alignment = 1 << (draw & 3);
                int size = 17 + (draw % 47);
                GpuRingAllocator.Allocation allocation = allocator.allocate(size, alignment);

                assertEquals(expectedSlot, allocation.slot());
                assertEquals(0, allocation.offsetBytes() % alignment);
                assertTrue(allocation.offsetBytes() >= previousEnd);
                assertTrue(allocation.offsetBytes() + allocation.lengthBytes()
                        <= (expectedSlot + 1) * slotBytes);
                previousEnd = allocation.offsetBytes() + allocation.lengthBytes();
            }
            allocator.advanceFrame();
        }
    }
}
