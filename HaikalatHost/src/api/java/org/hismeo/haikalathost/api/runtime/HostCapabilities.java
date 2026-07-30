package org.hismeo.haikalathost.api.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Immutable OpenGL capability snapshot that does not expose Haikalat implementation types.
 */
public record HostCapabilities(
        boolean contextAvailable,
        int majorVersion,
        int minorVersion,
        boolean coreProfile,
        String vendor,
        String renderer,
        String driverVersion,
        List<String> missingRequirements,
        List<String> optionalFeatures
) {
    private static final String UNAVAILABLE_VALUE = "unavailable";

    public HostCapabilities {
        vendor = Objects.requireNonNull(vendor, "vendor");
        renderer = Objects.requireNonNull(renderer, "renderer");
        driverVersion = Objects.requireNonNull(driverVersion, "driverVersion");
        missingRequirements = List.copyOf(
                Objects.requireNonNull(missingRequirements, "missingRequirements"));
        optionalFeatures = List.copyOf(
                Objects.requireNonNull(optionalFeatures, "optionalFeatures"));
    }

    public static HostCapabilities unavailable() {
        return new HostCapabilities(
                false,
                0,
                0,
                false,
                UNAVAILABLE_VALUE,
                UNAVAILABLE_VALUE,
                UNAVAILABLE_VALUE,
                List.of(),
                List.of());
    }

    public boolean meetsRequirements() {
        return contextAvailable && missingRequirements.isEmpty();
    }
}
