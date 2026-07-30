package org.hismeo.haikalathost.api.content;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable declaration; registering it does not load or upload the referenced asset.
 */
public record HaikalatAssetDefinition(
        ResourceLocation id,
        HaikalatAssetKind kind,
        String ownerModId
) {
    public HaikalatAssetDefinition {
        id = Objects.requireNonNull(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        ownerModId = Objects.requireNonNull(ownerModId, "ownerModId");
        if (ownerModId.isBlank()) {
            throw new IllegalArgumentException("ownerModId must not be blank");
        }
    }

    /**
     * Compatibility constructor for declarations whose owner is the asset namespace.
     */
    public HaikalatAssetDefinition(ResourceLocation id, HaikalatAssetKind kind) {
        this(id, kind, Objects.requireNonNull(id, "id").getNamespace());
    }
}
