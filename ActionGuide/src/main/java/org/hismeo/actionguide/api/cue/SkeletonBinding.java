package org.hismeo.actionguide.api.cue;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

import java.util.Optional;

public record SkeletonBinding(ResourceLocation id, int version, Optional<String> topologyHash) {
    public SkeletonBinding {
        ResourceIds.require(id, "skeleton id");
        if (version < 1) {
            throw new IllegalArgumentException("skeleton version must be positive");
        }
        topologyHash = topologyHash == null ? Optional.empty() : topologyHash.map(String::trim).filter(value -> !value.isEmpty());
    }
}
