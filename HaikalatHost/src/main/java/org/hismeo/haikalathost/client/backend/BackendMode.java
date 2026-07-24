package org.hismeo.haikalathost.client.backend;

public enum BackendMode {
    VANILLA,
    HAIKALAT;

    public static BackendMode parse(String value) {
        return switch (value == null ? "vanilla" : value.trim().toLowerCase()) {
            case "vanilla" -> VANILLA;
            case "haikalat" -> HAIKALAT;
            default -> throw new IllegalArgumentException(
                    "haikalathost.backend must be 'vanilla' or 'haikalat', found: " + value);
        };
    }
}
