package org.hismeo.haikalathost.client.backend;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullTakeoverConfigurationTest {
    @Test
    void defaultsToVanillaWithoutExperimentalGpuFeatures() {
        FullTakeoverConfiguration configuration = FullTakeoverConfiguration.from(Map.of());

        assertEquals(BackendMode.VANILLA, configuration.backend());
        assertEquals(ValidationMode.STRICT, configuration.validation());
        assertFalse(configuration.gpuDriven());
        assertFalse(configuration.bindless());
    }

    @Test
    void parsesStrictHaikalatSelection() {
        FullTakeoverConfiguration configuration = FullTakeoverConfiguration.from(Map.of(
                FullTakeoverConfiguration.BACKEND_PROPERTY, "haikalat",
                FullTakeoverConfiguration.GPU_DRIVEN_PROPERTY, "true",
                FullTakeoverConfiguration.BINDLESS_PROPERTY, "true"));

        assertTrue(configuration.haikalatBackend());
        assertTrue(configuration.gpuDriven());
        assertTrue(configuration.bindless());
    }

    @Test
    void rejectsUnknownModes() {
        assertThrows(IllegalArgumentException.class, () -> FullTakeoverConfiguration.from(Map.of(
                FullTakeoverConfiguration.BACKEND_PROPERTY, "hybrid")));
        assertThrows(IllegalArgumentException.class, () -> FullTakeoverConfiguration.from(Map.of(
                FullTakeoverConfiguration.VALIDATION_PROPERTY, "silent")));
    }
}
