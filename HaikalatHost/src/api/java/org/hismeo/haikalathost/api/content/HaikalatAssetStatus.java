package org.hismeo.haikalathost.api.content;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable asset status returned to integrations and diagnostics.
 *
 * <p>A prepared generation describes CPU data only. It does not claim that the asset has been
 * uploaded or attached to a render pipeline.</p>
 */
public record HaikalatAssetStatus(
        ResourceLocation id,
        HaikalatAssetKind kind,
        String ownerModId,
        HaikalatAssetState state,
        long requestedResourceGeneration,
        long preparedResourceGeneration,
        boolean lastKnownGood,
        String reasonCode,
        String message
) {
    public HaikalatAssetStatus {
        id = Objects.requireNonNull(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        ownerModId = requireText(ownerModId, "ownerModId");
        state = Objects.requireNonNull(state, "state");
        if (requestedResourceGeneration < -1L) {
            throw new IllegalArgumentException(
                    "requestedResourceGeneration must be -1 or non-negative");
        }
        if (preparedResourceGeneration < -1L) {
            throw new IllegalArgumentException(
                    "preparedResourceGeneration must be -1 or non-negative");
        }
        if (lastKnownGood && preparedResourceGeneration < 0L) {
            throw new IllegalArgumentException(
                    "lastKnownGood requires a prepared resource generation");
        }
        reasonCode = requireText(reasonCode, "reasonCode");
        message = Objects.requireNonNull(message, "message");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
