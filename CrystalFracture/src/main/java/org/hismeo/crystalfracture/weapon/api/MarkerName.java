package org.hismeo.crystalfracture.weapon.api;

import java.util.Objects;
import java.util.regex.Pattern;

public record MarkerName(String value) implements Comparable<MarkerName> {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9._/-]+");

    public MarkerName {
        Objects.requireNonNull(value, "value");
        if (!VALID_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid marker name: " + value);
        }
    }

    @Override
    public int compareTo(MarkerName other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
