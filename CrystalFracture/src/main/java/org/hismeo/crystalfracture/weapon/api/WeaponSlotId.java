package org.hismeo.crystalfracture.weapon.api;

import java.util.Objects;
import java.util.regex.Pattern;

public record WeaponSlotId(String value) implements Comparable<WeaponSlotId> {
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9._/-]+");

    public WeaponSlotId {
        Objects.requireNonNull(value, "value");
        if (!VALID_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid weapon slot id: " + value);
        }
    }

    @Override
    public int compareTo(WeaponSlotId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
