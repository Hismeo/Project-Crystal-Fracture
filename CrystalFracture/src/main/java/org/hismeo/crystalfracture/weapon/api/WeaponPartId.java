package org.hismeo.crystalfracture.weapon.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record WeaponPartId(ResourceLocation value) implements Comparable<WeaponPartId> {
    public WeaponPartId {
        Objects.requireNonNull(value, "value");
    }

    public static WeaponPartId parse(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid weapon part id: " + value);
        }
        return new WeaponPartId(parsed);
    }

    @Override
    public int compareTo(WeaponPartId other) {
        return value.toString().compareTo(other.value.toString());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
