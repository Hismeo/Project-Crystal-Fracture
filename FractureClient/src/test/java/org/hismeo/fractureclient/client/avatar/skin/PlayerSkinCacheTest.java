package org.hismeo.fractureclient.client.avatar.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class PlayerSkinCacheTest {
    @TempDir
    Path skinManagerRoot;

    @Test
    void matchingLayoutDoesNotCopyOrChangePixels() {
        int[] pixels = labeledSkin();

        int[] result = PlayerSkinLayoutMapper.remapArms(pixels, true, true);

        assertSame(pixels, result);
        assertArrayEquals(labeledSkin(), result);
    }

    @Test
    void classicToSlimRepackagesAllFourArmIslandsOnly() {
        int[] source = labeledSkin();

        int[] result = PlayerSkinLayoutMapper.remapArms(source, true, false);

        assertNotSame(source, result);
        assertEquals(source[pixel(0, 0)], result[pixel(0, 0)]);
        assertEquals(source[pixel(44, 20)], result[pixel(44, 20)]);
        assertEquals(source[pixel(47, 20)], result[pixel(46, 20)]);
        assertEquals(source[pixel(48, 20)], result[pixel(47, 20)]);
        assertEquals(0, result[pixel(54, 20)]);
        assertEquals(source[pixel(36, 52)], result[pixel(36, 52)]);
        assertEquals(source[pixel(52, 52)], result[pixel(52, 52)]);
    }

    @Test
    void slimToClassicExpandsArmFacesWithoutChangingOtherSkinPixels() {
        int[] source = labeledSkin();

        int[] result = PlayerSkinLayoutMapper.remapArms(source, false, true);

        assertEquals(source[pixel(10, 10)], result[pixel(10, 10)]);
        assertEquals(source[pixel(44, 20)], result[pixel(44, 20)]);
        assertEquals(source[pixel(46, 20)], result[pixel(47, 20)]);
        assertEquals(source[pixel(47, 20)], result[pixel(48, 20)]);
        assertEquals(source[pixel(51, 20)], result[pixel(52, 20)]);
    }

    @Test
    void downloadedSkinPathIsResolvedUnderSkinManagersAssetRoot() {
        Path result = PlayerSkinCache.resolveDownloadedSkinPath(
                skinManagerRoot,
                "https://textures.minecraft.net/texture/abc");

        assertEquals(
                skinManagerRoot.resolve("9f").resolve(
                        "9f04f41a848514162050e3d68c1a7abb441dc2b5"),
                result);
    }

    private static int[] labeledSkin() {
        int[] pixels = new int[64 * 64];
        Arrays.setAll(pixels, index -> index + 1);
        return pixels;
    }

    private static int pixel(int x, int y) {
        return y * 64 + x;
    }
}
