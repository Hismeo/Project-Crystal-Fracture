package org.hismeo.haikalathost.client.geometry;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalVertexEncoderTest {
    @Test
    void expandsMissingFieldsWithDeterministicDefaults() {
        ByteBuffer source = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder());
        source.putFloat(1.0F).putFloat(2.0F).putFloat(3.0F).flip();
        ByteBuffer target = ByteBuffer
                .allocate(CanonicalVertexLayout.STRIDE_BYTES)
                .order(ByteOrder.nativeOrder());

        CanonicalVertexEncoder.encode(
                source, new CanonicalSourceLayout(12, 0, -1, -1, -1, -1, -1), 1, target);
        target.flip();

        assertEquals(1.0F, target.getFloat(CanonicalVertexLayout.POSITION_OFFSET));
        assertEquals(2.0F, target.getFloat(CanonicalVertexLayout.POSITION_OFFSET + 4));
        assertEquals(3.0F, target.getFloat(CanonicalVertexLayout.POSITION_OFFSET + 8));
        assertEquals(0xFFFFFFFF, target.getInt(CanonicalVertexLayout.COLOR_OFFSET));
        assertEquals(0.0F, target.getFloat(CanonicalVertexLayout.UV_OFFSET));
        assertEquals(0, Short.toUnsignedInt(target.getShort(CanonicalVertexLayout.OVERLAY_OFFSET)));
        assertEquals(240, Short.toUnsignedInt(target.getShort(CanonicalVertexLayout.LIGHT_OFFSET)));
        assertEquals(127, target.get(CanonicalVertexLayout.NORMAL_OFFSET + 1));
    }

    @Test
    void copiesEveryCanonicalFieldWithoutChangingPackedBits() {
        ByteBuffer source = ByteBuffer.allocate(36).order(ByteOrder.nativeOrder());
        source.putFloat(4.0F).putFloat(5.0F).putFloat(6.0F);
        source.putInt(0x44332211);
        source.putFloat(0.25F).putFloat(0.75F);
        source.putShort((short) 10).putShort((short) 11);
        source.putShort((short) 12).putShort((short) 13);
        source.put((byte) 14).put((byte) 15).put((byte) 16).put((byte) 0).flip();
        ByteBuffer target = ByteBuffer.allocate(36).order(ByteOrder.nativeOrder());

        CanonicalVertexEncoder.encode(
                source, new CanonicalSourceLayout(36, 0, 12, 16, 24, 28, 32), 1, target);
        target.flip();

        assertEquals(0x44332211, target.getInt(12));
        assertEquals(0.25F, target.getFloat(16));
        assertEquals(11, target.getShort(26));
        assertEquals(13, target.getShort(30));
        assertEquals(16, target.get(34));
    }

    @Test
    void validatesSourceAndDestinationOwnershipBounds() {
        CanonicalSourceLayout layout = new CanonicalSourceLayout(12, 0, -1, -1, -1, -1, -1);
        assertThrows(IllegalArgumentException.class, () ->
                CanonicalVertexEncoder.encode(ByteBuffer.allocate(11), layout, 1, ByteBuffer.allocate(36)));
        assertThrows(IllegalArgumentException.class, () ->
                CanonicalVertexEncoder.encode(ByteBuffer.allocate(12), layout, 1, ByteBuffer.allocate(35)));
    }
}
