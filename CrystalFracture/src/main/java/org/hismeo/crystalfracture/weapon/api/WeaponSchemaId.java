package org.hismeo.crystalfracture.weapon.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record WeaponSchemaId(ResourceLocation value) implements Comparable<WeaponSchemaId> {
    public WeaponSchemaId {
        Objects.requireNonNull(value, "value");
    }

    public static WeaponSchemaId parse(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid weapon schema id: " + value);
        }
        return new WeaponSchemaId(parsed);
    }

    @Override
    public int compareTo(WeaponSchemaId other) {
        return value.toString().compareTo(other.value.toString());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
