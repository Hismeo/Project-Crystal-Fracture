package org.hismeo.haikalathost.api.content;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe snapshot service for declaratively registered content.
 */
@FunctionalInterface
public interface HaikalatAssets {
    /**
     * Returns a detached immutable snapshot sorted by asset id.
     */
    List<HaikalatAssetStatus> all();

    default Optional<HaikalatAssetStatus> find(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return all().stream().filter(status -> status.id().equals(id)).findFirst();
    }

    static HaikalatAssets empty() {
        return List::of;
    }
}
