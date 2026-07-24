package org.hismeo.haikalathost.client.runtime;

import java.util.Map;
import java.util.Objects;

public record HaikalatHostConfiguration(
        boolean enabled,
        boolean immediateCompatibility,
        boolean chunkMirroring
) {
    public static final String ENABLED_PROPERTY = "haikalathost.enabled";
    public static final String IMMEDIATE_COMPATIBILITY_PROPERTY = "haikalathost.compatDraws";
    public static final String CHUNK_MIRRORING_PROPERTY = "haikalathost.chunkMeshes";

    private static final HaikalatHostConfiguration CURRENT = fromSystemProperties();

    public static HaikalatHostConfiguration current() {
        return CURRENT;
    }

    public static HaikalatHostConfiguration from(Map<String, String> properties) {
        Objects.requireNonNull(properties, "properties");
        boolean enabled = parse(properties.get(ENABLED_PROPERTY), true);
        if (!enabled) return new HaikalatHostConfiguration(false, false, false);
        return new HaikalatHostConfiguration(
                true,
                parse(properties.get(IMMEDIATE_COMPATIBILITY_PROPERTY), false),
                parse(properties.get(CHUNK_MIRRORING_PROPERTY), false));
    }

    public boolean hasRenderInterception() {
        return immediateCompatibility || chunkMirroring;
    }

    private static HaikalatHostConfiguration fromSystemProperties() {
        return from(Map.of(
                ENABLED_PROPERTY, System.getProperty(ENABLED_PROPERTY, "true"),
                IMMEDIATE_COMPATIBILITY_PROPERTY,
                System.getProperty(IMMEDIATE_COMPATIBILITY_PROPERTY, "false"),
                CHUNK_MIRRORING_PROPERTY,
                System.getProperty(CHUNK_MIRRORING_PROPERTY, "false")));
    }

    private static boolean parse(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
