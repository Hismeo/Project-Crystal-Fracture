package org.hismeo.haikalathost.client.extraction;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Extraction-side bridge from Minecraft resource keys to generation-local Host texture IDs. */
public final class TextureResourceRegistry {
    private final Map<ResourceLocation, Integer> identifiers = new HashMap<>();
    private final List<ResourceLocation> resources = new ArrayList<>();

    public int resolve(ResourceLocation location) {
        Objects.requireNonNull(location, "location");
        Integer existing = identifiers.get(location);
        if (existing != null) return existing;
        int created = identifiers.size();
        identifiers.put(location, created);
        resources.add(location);
        return created;
    }

    public ResourceLocation get(int identifier) {
        if (identifier < 0 || identifier >= resources.size()) {
            throw new IllegalArgumentException("unknown texture resource id " + identifier);
        }
        return resources.get(identifier);
    }
    public int size() {
        return identifiers.size();
    }

    public void clear() {
        identifiers.clear();
        resources.clear();
    }
}
