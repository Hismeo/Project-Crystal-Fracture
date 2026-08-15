package org.hismeo.crystalfracture.weapon.api;

import java.util.Objects;
import java.util.regex.Pattern;

public record ConnectName(String value) implements Comparable<ConnectName> {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9._/-]+");

    public ConnectName {
        Objects.requireNonNull(value, "value");
        if (!VALID_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid connect name: " + value);
        }
    }

    @Override
    public int compareTo(ConnectName other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
