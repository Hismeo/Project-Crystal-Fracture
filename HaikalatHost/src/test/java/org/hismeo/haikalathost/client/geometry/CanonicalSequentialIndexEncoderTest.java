package org.hismeo.haikalathost.client.geometry;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalSequentialIndexEncoderTest {
    @Test
    void emitsMinecraftEquivalentQuadAndLinePatterns() {
        assertArrayEquals(
                new int[]{0, 1, 2, 2, 3, 0},
                encode(SequentialIndexPattern.QUADS));
        assertArrayEquals(
                new int[]{0, 1, 2, 3, 2, 1},
                encode(SequentialIndexPattern.LINES));
    }

    @Test
    void validatesPatternShapeAndSixteenBitRange() {
        assertThrows(IllegalArgumentException.class, () ->
                CanonicalSequentialIndexEncoder.encode(
                        SequentialIndexPattern.QUADS,
                        5,
                        CanonicalIndexType.UNSIGNED_INT,
                        ByteBuffer.allocate(20)));
        assertThrows(IllegalArgumentException.class, () ->
                CanonicalSequentialIndexEncoder.encode(
                        SequentialIndexPattern.IDENTITY,
                        65_537,
                        CanonicalIndexType.UNSIGNED_SHORT,
                        ByteBuffer.allocate(65_537 * 2)));
    }

    private static int[] encode(SequentialIndexPattern pattern) {
        ByteBuffer buffer = ByteBuffer.allocate(24).order(ByteOrder.nativeOrder());
        CanonicalSequentialIndexEncoder.encode(
                pattern, 6, CanonicalIndexType.UNSIGNED_INT, buffer);
        buffer.flip();
        int[] result = new int[6];
        for (int index = 0; index < result.length; index++) result[index] = buffer.getInt();
        return result;
    }
}
