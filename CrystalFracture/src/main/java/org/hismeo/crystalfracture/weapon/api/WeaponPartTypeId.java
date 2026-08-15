package org.hismeo.crystalfracture.weapon.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record WeaponPartTypeId(ResourceLocation value) implements Comparable<WeaponPartTypeId> {
    public WeaponPartTypeId {
        Objects.requireNonNull(value, "value");
    }

    public static WeaponPartTypeId parse(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid weapon part type id: " + value);
        }
        return new WeaponPartTypeId(parsed);
    }

    @Override
    public int compareTo(WeaponPartTypeId other) {
        return value.toString().compareTo(other.value.toString());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
