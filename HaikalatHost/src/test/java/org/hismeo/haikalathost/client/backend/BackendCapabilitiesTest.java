package org.hismeo.haikalathost.client.backend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendCapabilitiesTest {
    private static final FullTakeoverConfiguration REQUIRED =
            new FullTakeoverConfiguration(BackendMode.HAIKALAT, ValidationMode.STRICT, true, true);

    @Test
    void reportsEveryMissingHardRequirement() {
        BackendCapabilities capabilities = new BackendCapabilities(
                false, false, false, false, false, false, false, false, false);

        List<String> missing = capabilities.missingRequirements(REQUIRED);

        assertEquals(9, missing.size());
        assertTrue(capabilities.report(REQUIRED).contains("OpenGL 4.6"));
        assertTrue(capabilities.report(REQUIRED).contains("ARB_bindless_texture"));
    }

    @Test
    void bindlessIsOnlyRequiredWhenSelected() {
        BackendCapabilities capabilities = new BackendCapabilities(
                true, true, true, true, true, true, true, true, false);
        FullTakeoverConfiguration noBindless =
                new FullTakeoverConfiguration(BackendMode.HAIKALAT, ValidationMode.STRICT, true, false);

        assertTrue(capabilities.missingRequirements(noBindless).isEmpty());
        assertEquals(List.of("ARB_bindless_texture"), capabilities.missingRequirements(REQUIRED));
    }
}
