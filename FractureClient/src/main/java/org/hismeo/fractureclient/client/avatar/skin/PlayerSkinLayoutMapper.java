package org.hismeo.fractureclient.client.avatar.skin;

import java.util.Objects;

/** Pure pixel-layout conversion between Minecraft's Classic and Slim arm UV islands. */
final class PlayerSkinLayoutMapper {
    private static final int SKIN_WIDTH = 64;

    private PlayerSkinLayoutMapper() {
    }

    /**
     * Re-packs only the four arm islands. Avatar identity stays independent from Steve/Alex skin
     * metadata while either Minecraft skin layout remains usable with either authored model.
     */
    static int[] remapArms(int[] source, boolean sourceClassic, boolean targetClassic) {
        Objects.requireNonNull(source, "source");
        if (source.length != SKIN_WIDTH * SKIN_WIDTH) {
            throw new IllegalArgumentException("Minecraft skin pixels must be 64x64");
        }
        if (sourceClassic == targetClassic) {
            return source;
        }
        int sourceWidth = sourceClassic ? 4 : 3;
        int targetWidth = targetClassic ? 4 : 3;
        int[] destination = source.clone();
        remapArmIsland(source, destination, 40, 16, sourceWidth, targetWidth);
        remapArmIsland(source, destination, 40, 32, sourceWidth, targetWidth);
        remapArmIsland(source, destination, 32, 48, sourceWidth, targetWidth);
        remapArmIsland(source, destination, 48, 48, sourceWidth, targetWidth);
        return destination;
    }

    private static void remapArmIsland(
            int[] source,
            int[] destination,
            int originX,
            int originY,
            int sourceWidth,
            int targetWidth
    ) {
        fillRect(destination, originX, originY, 16, 16, 0);
        copyScaled(source, destination,
                originX + 4, originY, sourceWidth, 4,
                originX + 4, originY, targetWidth, 4);
        copyScaled(source, destination,
                originX + 4 + sourceWidth, originY, sourceWidth, 4,
                originX + 4 + targetWidth, originY, targetWidth, 4);
        copyScaled(source, destination,
                originX, originY + 4, 4, 12,
                originX, originY + 4, 4, 12);
        copyScaled(source, destination,
                originX + 4, originY + 4, sourceWidth, 12,
                originX + 4, originY + 4, targetWidth, 12);
        copyScaled(source, destination,
                originX + 4 + sourceWidth, originY + 4, 4, 12,
                originX + 4 + targetWidth, originY + 4, 4, 12);
        copyScaled(source, destination,
                originX + 8 + sourceWidth, originY + 4, sourceWidth, 12,
                originX + 8 + targetWidth, originY + 4, targetWidth, 12);
    }

    private static void copyScaled(
            int[] source,
            int[] destination,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            int destinationX,
            int destinationY,
            int destinationWidth,
            int destinationHeight
    ) {
        for (int y = 0; y < destinationHeight; y++) {
            int sampledY = sourceY + ((2 * y + 1) * sourceHeight) / (2 * destinationHeight);
            for (int x = 0; x < destinationWidth; x++) {
                int sampledX = sourceX + ((2 * x + 1) * sourceWidth) / (2 * destinationWidth);
                destination[(destinationY + y) * SKIN_WIDTH + destinationX + x] =
                        source[sampledY * SKIN_WIDTH + sampledX];
            }
        }
    }

    private static void fillRect(
            int[] pixels,
            int originX,
            int originY,
            int width,
            int height,
            int value
    ) {
        for (int y = originY; y < originY + height; y++) {
            for (int x = originX; x < originX + width; x++) {
                pixels[y * SKIN_WIDTH + x] = value;
            }
        }
    }
}
