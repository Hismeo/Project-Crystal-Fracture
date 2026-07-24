package org.hismeo.haikalathost.client.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaikalatHostConfigurationTest {
    @Test
    void defaultsToPassiveMode() {
        HaikalatHostConfiguration configuration = HaikalatHostConfiguration.from(Map.of());

        assertTrue(configuration.enabled());
        assertFalse(configuration.immediateCompatibility());
        assertFalse(configuration.chunkMirroring());
        assertFalse(configuration.hasRenderInterception());
    }

    @Test
    void globalDisableOverridesIndividualInterceptors() {
        HaikalatHostConfiguration configuration = HaikalatHostConfiguration.from(Map.of(
                HaikalatHostConfiguration.ENABLED_PROPERTY, "false",
                HaikalatHostConfiguration.IMMEDIATE_COMPATIBILITY_PROPERTY, "true",
                HaikalatHostConfiguration.CHUNK_MIRRORING_PROPERTY, "true"));

        assertFalse(configuration.enabled());
        assertFalse(configuration.immediateCompatibility());
        assertFalse(configuration.chunkMirroring());
        assertFalse(configuration.hasRenderInterception());
    }

    @Test
    void interceptorsAreExplicitOptIns() {
        HaikalatHostConfiguration immediate = HaikalatHostConfiguration.from(Map.of(
                HaikalatHostConfiguration.IMMEDIATE_COMPATIBILITY_PROPERTY, "true"));
        HaikalatHostConfiguration chunks = HaikalatHostConfiguration.from(Map.of(
                HaikalatHostConfiguration.CHUNK_MIRRORING_PROPERTY, "true"));

        assertTrue(immediate.immediateCompatibility());
        assertTrue(immediate.hasRenderInterception());
        assertTrue(chunks.chunkMirroring());
        assertTrue(chunks.hasRenderInterception());
    }
}
