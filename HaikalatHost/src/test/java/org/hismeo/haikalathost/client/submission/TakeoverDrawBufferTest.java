package org.hismeo.haikalathost.client.submission;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TakeoverDrawBufferTest {
    @Test
    void stableRadixSortAppliesDomainSpecificOrdering() {
        TakeoverDrawBuffer draws = new TakeoverDrawBuffer(2);
        append(draws, PassKey.WORLD_OPAQUE, 2, 0, 0.0F, 0);
        append(draws, PassKey.WORLD_OPAQUE, 1, 5, 0.0F, 1);
        append(draws, PassKey.WORLD_OPAQUE, 1, 2, 0.0F, 2);
        append(draws, PassKey.WORLD_TRANSLUCENT, 7, 9, 2.0F, 3);
        append(draws, PassKey.WORLD_TRANSLUCENT, 3, 1, 10.0F, 4);
        append(draws, PassKey.WORLD_TRANSLUCENT, 1, 0, -1.0F, 5);
        append(draws, PassKey.UI, 5, 3, 0.0F, 7);
        append(draws, PassKey.UI, 1, 1, 0.0F, 6);

        assertThrows(IllegalStateException.class, () -> draws.orderedDraw(0));
        draws.sortAndBuildBatches();

        assertEquals(2L, draws.orderedOriginalSequence(0));
        assertEquals(1L, draws.orderedOriginalSequence(1));
        assertEquals(0L, draws.orderedOriginalSequence(2));
        assertEquals(4L, draws.orderedOriginalSequence(3));
        assertEquals(3L, draws.orderedOriginalSequence(4));
        assertEquals(5L, draws.orderedOriginalSequence(5));
        assertEquals(6L, draws.orderedOriginalSequence(6));
        assertEquals(7L, draws.orderedOriginalSequence(7));
    }

    @Test
    void buildsOnlyStateCompatibleBatchesAndEncodesIndirectCommands() {
        TakeoverDrawBuffer draws = new TakeoverDrawBuffer(4);
        draws.append(PassKey.WORLD_OPAQUE, 1, 2, 0, 0, 4, 5125,
                6, 1, 3, 7, 11, 0.0F, 0);
        draws.append(PassKey.WORLD_OPAQUE, 1, 2, 0, 0, 4, 5125,
                12, 2, 9, 8, 13, 0.0F, 1);
        draws.append(PassKey.WORLD_OPAQUE, 1, 2, 1, 0, 4, 5125,
                18, 1, 15, 9, 17, 0.0F, 2);
        draws.sortAndBuildBatches();

        assertEquals(2, draws.batchCount());
        assertEquals(2, draws.batchLength(0));
        assertEquals(1, draws.batchLength(1));

        ByteBuffer encoded = ByteBuffer
                .allocate(2 * DrawElementsIndirectCommand.BYTES)
                .order(ByteOrder.nativeOrder());
        draws.writeIndirectCommands(encoded, draws.batchStart(0), draws.batchLength(0));
        encoded.flip();

        assertEquals(6, encoded.getInt());
        assertEquals(1, encoded.getInt());
        assertEquals(3, encoded.getInt());
        assertEquals(7, encoded.getInt());
        assertEquals(11, encoded.getInt());
        assertEquals(12, encoded.getInt());
        assertEquals(2, encoded.getInt());
        assertEquals(9, encoded.getInt());
        assertEquals(8, encoded.getInt());
        assertEquals(13, encoded.getInt());
    }

    @Test
    void drawDataPageIsAnMdiBatchBoundary() {
        TakeoverDrawBuffer draws = new TakeoverDrawBuffer(2);
        draws.append(PassKey.WORLD_OPAQUE, 1, 2, 0, 0, 4, 5125,
                6, 1, 0, 0, 0, 0.0F, 0);
        draws.append(PassKey.WORLD_OPAQUE, 1, 2, 0, 1, 4, 5125,
                6, 1, 0, 0, 0, 0.0F, 1);

        draws.sortAndBuildBatches();

        assertEquals(2, draws.batchCount());
        assertEquals(0, draws.orderedDrawDataPage(draws.batchStart(0)));
        assertEquals(1, draws.orderedDrawDataPage(draws.batchStart(1)));
    }

    private static void append(
            TakeoverDrawBuffer draws,
            PassKey pass,
            int pipeline,
            int material,
            float depth,
            long sequence
    ) {
        draws.append(pass, pipeline, material, 0, 0, 4, 5125,
                6, 1, 0, 0, 0, depth, sequence);
    }
}
