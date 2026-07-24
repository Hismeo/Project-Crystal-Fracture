package org.hismeo.haikalathost.client.gpu;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuDrawDataTest {
    @Test
    void staticWriterAppendsContiguousRecordsWithoutTemporaryObjects() {
        ByteBuffer target = ByteBuffer.allocate(2 * GpuDrawData.BYTES)
                .order(ByteOrder.nativeOrder());

        GpuDrawData.writeTo(target, 2, 3, 4, 5.0F, 6.0F, 7.0F);
        GpuDrawData.writeTo(target, 8, 9, 10, 11.0F, 12.0F, 13.0F);

        assertEquals(2 * GpuDrawData.BYTES, target.position());
        target.flip();
        assertRecord(target, 2, 3, 4, 5.0F, 6.0F, 7.0F);
        assertRecord(target, 8, 9, 10, 11.0F, 12.0F, 13.0F);
    }

    private static void assertRecord(
            ByteBuffer target,
            int transform,
            int material,
            int flags,
            float x,
            float y,
            float z
    ) {
        assertEquals(transform, target.getInt());
        assertEquals(material, target.getInt());
        assertEquals(flags, target.getInt());
        assertEquals(0, target.getInt());
        assertEquals(x, target.getFloat());
        assertEquals(y, target.getFloat());
        assertEquals(z, target.getFloat());
        assertEquals(0.0F, target.getFloat());
    }
}
