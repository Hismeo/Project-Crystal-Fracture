package org.hismeo.haikalathost.internal.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftRenderTargetDescriptorTest {
    @Test
    void representsAZeroSizedMinimizedViewWithoutLosingBorrowedAttachments() {
        MinecraftRenderTargetDescriptor target = new MinecraftRenderTargetDescriptor(
                11,
                12,
                13,
                0,
                0,
                1920,
                1080,
                true,
                false);

        assertTrue(target.minimized());
    }

    @Test
    void rejectsNegativeViewDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MinecraftRenderTargetDescriptor(
                        11, 12, -1, -1, 0, 1920, 1080, false, false));
    }

    @Test
    void reportsNormalViewsAsRenderable() {
        MinecraftRenderTargetDescriptor target = new MinecraftRenderTargetDescriptor(
                11,
                12,
                -1,
                1280,
                720,
                1280,
                720,
                false,
                false);

        assertFalse(target.minimized());
    }
}
