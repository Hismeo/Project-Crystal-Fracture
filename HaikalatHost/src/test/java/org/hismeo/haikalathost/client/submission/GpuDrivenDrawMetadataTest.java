package org.hismeo.haikalathost.client.submission;

import org.hismeo.haikalathost.client.gpu.GpuCullingMetadata;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuDrivenDrawMetadataTest {
    @Test
    void encodesCullingAndCompactControlInSortedIndirectOrder() {
        TakeoverDrawBuffer draws = new TakeoverDrawBuffer(2);
        int opaque = draws.append(
                PassKey.WORLD_OPAQUE, 1, 2, 0, 0, 4, 5125,
                6, 1, 0, 0, 7, 0.0F, 0);
        draws.setCullingSphere(opaque, 3, 8.0F, 9.0F, 10.0F, 13.0F);
        draws.append(
                PassKey.UI, 1, 2, 0, 0, 4, 5125,
                6, 1, 0, 0, 9, 0.0F, 1);
        draws.sortAndBuildBatches();

        assertTrue(draws.batchGpuCompactable(0));
        assertFalse(draws.batchGpuCompactable(1));

        ByteBuffer encoded = ByteBuffer
                .allocate(draws.size() * GpuCullingMetadata.BYTES)
                .order(ByteOrder.nativeOrder());
        draws.writeGpuCullingMetadata(encoded);
        encoded.flip();

        assertEquals(8.0F, encoded.getFloat());
        assertEquals(9.0F, encoded.getFloat());
        assertEquals(10.0F, encoded.getFloat());
        assertEquals(13.0F, encoded.getFloat());
        assertEquals(3, encoded.getInt());
        assertEquals(0, encoded.getInt());
        assertEquals(0, encoded.getInt());
        assertEquals(
                GpuCullingMetadata.FLAG_FRUSTUM_SPHERE | GpuCullingMetadata.FLAG_COMPACT,
                encoded.getInt());

        assertEquals(0.0F, encoded.getFloat());
        assertEquals(0.0F, encoded.getFloat());
        assertEquals(0.0F, encoded.getFloat());
        assertEquals(0.0F, encoded.getFloat());
        assertEquals(0, encoded.getInt());
        assertEquals(1, encoded.getInt());
        assertEquals(1, encoded.getInt());
        assertEquals(0, encoded.getInt());
    }

    @Test
    void rejectsInvalidBoundsAndUndersizedTargets() {
        TakeoverDrawBuffer draws = new TakeoverDrawBuffer(1);
        int draw = draws.append(
                PassKey.WORLD_OPAQUE, 1, 2, 0, 0, 4, 5125,
                6, 1, 0, 0, 0, 0.0F, 0);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> draws.setCullingSphere(draw, 0, 0.0F, 0.0F, 0.0F, -1.0F));
        draws.sortAndBuildBatches();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> draws.writeGpuCullingMetadata(
                        ByteBuffer.allocate(GpuCullingMetadata.BYTES - 1)));
    }
}
