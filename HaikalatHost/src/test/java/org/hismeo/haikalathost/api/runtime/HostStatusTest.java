package org.hismeo.haikalathost.api.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostStatusTest {
    @Test
    void capabilityCollectionsAreDefensivelyCopied() {
        List<String> missing = new ArrayList<>(List.of("compute shaders"));
        HostCapabilities capabilities = new HostCapabilities(
                true,
                4,
                6,
                true,
                "vendor",
                "renderer",
                "driver",
                missing,
                List.of("bindless textures"));

        missing.clear();

        assertEquals(List.of("compute shaders"), capabilities.missingRequirements());
        assertThrows(
                UnsupportedOperationException.class,
                () -> capabilities.missingRequirements().add("other"));
        assertFalse(capabilities.meetsRequirements());
    }

    @Test
    void availabilityComesFromLifecycleState() {
        HostStatus ready = new HostStatus(
                HostLifecycleState.READY,
                "ready",
                "ready",
                new HostCapabilities(
                        true,
                        4,
                        6,
                        true,
                        "vendor",
                        "renderer",
                        "driver",
                        List.of(),
                        List.of()),
                2L);

        assertTrue(ready.available());
        assertFalse(HostStatus.notStarted().available());
        assertFalse(HostStatus.unavailable("failure", "failure").available());
    }
}
