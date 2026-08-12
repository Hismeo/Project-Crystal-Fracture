package org.hismeo.actionguide.api.runtime;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record ActionRequestResult(boolean accepted, ResourceLocation reason, Optional<ActionInstanceView> action) {
    public ActionRequestResult {
        Objects.requireNonNull(reason, "reason");
        action = action == null ? Optional.empty() : action;
    }
}
