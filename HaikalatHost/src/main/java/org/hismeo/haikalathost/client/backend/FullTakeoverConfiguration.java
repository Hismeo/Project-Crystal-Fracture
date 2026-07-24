package org.hismeo.haikalathost.client.backend;

import java.util.Map;
import java.util.Objects;

/** Immutable startup selection; backend ownership cannot change after Mixin transformation. */
public record FullTakeoverConfiguration(
        BackendMode backend,
        ValidationMode validation,
        boolean gpuDriven,
        boolean bindless
) {
    public static final String BACKEND_PROPERTY = "haikalathost.backend";
    public static final String VALIDATION_PROPERTY = "haikalathost.validation";
    public static final String GPU_DRIVEN_PROPERTY = "haikalathost.gpuDriven";
    public static final String BINDLESS_PROPERTY = "haikalathost.bindless";

    private static final FullTakeoverConfiguration CURRENT = fromSystemProperties();

    public FullTakeoverConfiguration {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(validation, "validation");
        if (backend == BackendMode.VANILLA && (bindless || gpuDriven)) {
            gpuDriven = false;
            bindless = false;
        }
    }

    public static FullTakeoverConfiguration current() {
        return CURRENT;
    }

    public static FullTakeoverConfiguration from(Map<String, String> properties) {
        Objects.requireNonNull(properties, "properties");
        BackendMode backend = BackendMode.parse(properties.get(BACKEND_PROPERTY));
        return new FullTakeoverConfiguration(
                backend,
                ValidationMode.parse(properties.get(VALIDATION_PROPERTY)),
                parseBoolean(properties.get(GPU_DRIVEN_PROPERTY), backend == BackendMode.HAIKALAT),
                parseBoolean(properties.get(BINDLESS_PROPERTY), false));
    }

    public boolean haikalatBackend() {
        return backend == BackendMode.HAIKALAT;
    }

    private static FullTakeoverConfiguration fromSystemProperties() {
        return from(Map.of(
                BACKEND_PROPERTY, System.getProperty(BACKEND_PROPERTY, "vanilla"),
                VALIDATION_PROPERTY, System.getProperty(VALIDATION_PROPERTY, "strict"),
                GPU_DRIVEN_PROPERTY, System.getProperty(GPU_DRIVEN_PROPERTY, "true"),
                BINDLESS_PROPERTY, System.getProperty(BINDLESS_PROPERTY, "false")));
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
