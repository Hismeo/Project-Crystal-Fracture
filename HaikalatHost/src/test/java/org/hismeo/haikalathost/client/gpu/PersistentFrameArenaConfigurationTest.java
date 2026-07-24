package org.hismeo.haikalathost.client.gpu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistentFrameArenaConfigurationTest {
    @Test
    void mapsEveryRegionToItsOwnPerSlotCapacity() {
        PersistentFrameArenaConfiguration configuration =
                new PersistentFrameArenaConfiguration(3, 100, 200, 300, 400, 500, 64);

        assertEquals(100, configuration.bytesPerSlot(FrameArenaRegion.VERTEX));
        assertEquals(200, configuration.bytesPerSlot(FrameArenaRegion.INDEX));
        assertEquals(300, configuration.bytesPerSlot(FrameArenaRegion.INSTANCE));
        assertEquals(400, configuration.bytesPerSlot(FrameArenaRegion.DRAW_DATA));
        assertEquals(500, configuration.bytesPerSlot(FrameArenaRegion.INDIRECT_COMMAND));
    }

    @Test
    void rejectsUnsafeSlotCountsAndOverflowingTotals() {
        assertThrows(IllegalArgumentException.class, () ->
                new PersistentFrameArenaConfiguration(1, 1, 1, 1, 1, 1, 1));
        assertThrows(ArithmeticException.class, () ->
                new PersistentFrameArenaConfiguration(
                        8, Integer.MAX_VALUE, 1, 1, 1, 1, 1));
    }
}
