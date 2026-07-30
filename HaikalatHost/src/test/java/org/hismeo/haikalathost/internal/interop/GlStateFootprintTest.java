package org.hismeo.haikalathost.internal.interop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlStateFootprintTest {
    @Test
    void minimalProbeHasNoIndexedOrAuxiliaryBindings() {
        GlStateFootprint footprint = GlStateFootprint.MINIMAL_PROBE;

        assertArrayEquals(new int[0], footprint.textureUnits());
        assertArrayEquals(new int[0], footprint.uniformBufferBindings());
        assertArrayEquals(new int[0], footprint.storageBufferBindings());
        assertArrayEquals(new int[0], footprint.imageUnits());
        assertFalse(footprint.capturesArrayBufferBinding());
        assertFalse(footprint.capturesReadBufferSelection());
        assertDoesNotThrow(() -> footprint.validateAgainstLimits(0, 0, 0, 0));
    }

    @Test
    void standardPipelineMatchesTheAuditedHaikalat020Bindings() {
        GlStateFootprint footprint = GlStateFootprint.STANDARD_PIPELINE;

        assertArrayEquals(
                new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13},
                footprint.textureUnits());
        assertArrayEquals(new int[]{0}, footprint.uniformBufferBindings());
        assertArrayEquals(new int[]{0, 7, 8, 9}, footprint.storageBufferBindings());
        assertArrayEquals(new int[]{0}, footprint.imageUnits());
        assertTrue(footprint.capturesArrayBufferBinding());
        assertTrue(footprint.capturesReadBufferSelection());
        assertDoesNotThrow(() -> footprint.validateAgainstLimits(14, 1, 10, 1));
    }

    @Test
    void builderSortsDeduplicatesAndDefensivelyCopiesBindings() {
        int[] source = {7, 2, 7, 4};
        GlStateFootprint footprint = GlStateFootprint.builder()
                .textureUnits(source)
                .uniformBufferBindings(3, 1, 3)
                .storageBufferBindings(9, 0, 9)
                .imageUnits(2, 1, 2)
                .build();
        source[0] = 99;

        assertArrayEquals(new int[]{2, 4, 7}, footprint.textureUnits());
        assertArrayEquals(new int[]{1, 3}, footprint.uniformBufferBindings());
        assertArrayEquals(new int[]{0, 9}, footprint.storageBufferBindings());
        assertArrayEquals(new int[]{1, 2}, footprint.imageUnits());

        int[] returned = footprint.textureUnits();
        returned[0] = 99;
        assertArrayEquals(new int[]{2, 4, 7}, footprint.textureUnits());
    }

    @Test
    void standardFootprintCanBeExplicitlyExtendedForCustomCommands() {
        GlStateFootprint extended = GlStateFootprint.STANDARD_PIPELINE.toBuilder()
                .textureUnits(21)
                .uniformBufferBindings(4)
                .storageBufferBindings(12)
                .imageUnits(3)
                .build();

        assertArrayEquals(
                new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 21},
                extended.textureUnits());
        assertArrayEquals(new int[]{0, 4}, extended.uniformBufferBindings());
        assertArrayEquals(new int[]{0, 7, 8, 9, 12}, extended.storageBufferBindings());
        assertArrayEquals(new int[]{0, 3}, extended.imageUnits());
    }

    @Test
    void rejectsNegativeBindingsAndDeviceLimitsThatAreTooSmall() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GlStateFootprint.builder().textureUnits(-1));

        GlStateFootprint standard = GlStateFootprint.STANDARD_PIPELINE;
        assertThrows(
                IllegalStateException.class,
                () -> standard.validateAgainstLimits(13, 1, 10, 1));
        assertThrows(
                IllegalStateException.class,
                () -> standard.validateAgainstLimits(14, 0, 10, 1));
        assertThrows(
                IllegalStateException.class,
                () -> standard.validateAgainstLimits(14, 1, 9, 1));
        assertThrows(
                IllegalStateException.class,
                () -> standard.validateAgainstLimits(14, 1, 10, 0));
    }
}
