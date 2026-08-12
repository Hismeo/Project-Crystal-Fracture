package org.hismeo.actionguide.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class ResourceIds {
    private ResourceIds() {
    }

    public static ResourceLocation require(ResourceLocation value, String label) {
        return Objects.requireNonNull(value, label);
    }

    public static ResourceLocation parse(String value, String label) {
        Objects.requireNonNull(value, label);
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) {
            throw new IllegalArgumentException(label + " is not a valid resource location: " + value);
        }
        return result;
    }

    public static String requireLocalId(String value, String label) {
        Objects.requireNonNull(value, label);
        String result = value.trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return result;
    }
}
