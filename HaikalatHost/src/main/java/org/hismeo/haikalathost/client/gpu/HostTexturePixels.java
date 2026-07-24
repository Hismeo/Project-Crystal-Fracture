package org.hismeo.haikalathost.client.gpu;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class HostTexturePixels {
    private HostTexturePixels() {
    }

    static ByteBuffer toBottomUpRgba(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int byteCount = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        ByteBuffer pixels = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
        for (int destinationY = 0; destinationY < height; destinationY++) {
            int sourceY = height - 1 - destinationY;
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, sourceY);
                pixels.put((byte) (argb >>> 16));
                pixels.put((byte) (argb >>> 8));
                pixels.put((byte) argb);
                pixels.put((byte) (argb >>> 24));
            }
        }
        return pixels.flip();
    }
}
