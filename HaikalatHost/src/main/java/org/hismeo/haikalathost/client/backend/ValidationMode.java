package org.hismeo.haikalathost.client.backend;

public enum ValidationMode {
    STRICT,
    DIAGNOSTIC;

    public static ValidationMode parse(String value) {
        return switch (value == null ? "strict" : value.trim().toLowerCase()) {
            case "strict" -> STRICT;
            case "diagnostic" -> DIAGNOSTIC;
            default -> throw new IllegalArgumentException(
                    "haikalathost.validation must be 'strict' or 'diagnostic', found: " + value);
        };
    }
}
