package org.hismeo.haikalathost.client.gpu;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class HostTextureManagerTest {
    @Test
    void convertsArgbToBottomUpRgbaWithoutNativeDecoding() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        image.setRGB(1, 0, 0xFF00FF00);
        image.setRGB(0, 1, 0xFF0000FF);
        image.setRGB(1, 1, 0x80FFFFFF);

        ByteBuffer pixels = HostTexturePixels.toBottomUpRgba(image);
        byte[] actual = new byte[pixels.remaining()];
        pixels.get(actual);

        assertArrayEquals(new byte[] {
                0, 0, (byte) 255, (byte) 255,
                (byte) 255, (byte) 255, (byte) 255, (byte) 128,
                (byte) 255, 0, 0, (byte) 255,
                0, (byte) 255, 0, (byte) 255
        }, actual);
    }
}
