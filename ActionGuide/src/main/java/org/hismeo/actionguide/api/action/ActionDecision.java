package org.hismeo.actionguide.api.action;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

import java.util.Objects;

public record ActionDecision(boolean accepted, ResourceLocation reason) {
    public static final ResourceLocation ACCEPTED = ResourceIds.parse("action_guide:accepted", "accepted reason");

    public ActionDecision {
        Objects.requireNonNull(reason, "reason");
    }

    public static ActionDecision allow() {
        return new ActionDecision(true, ACCEPTED);
    }

    public static ActionDecision reject(ResourceLocation reason) {
        return new ActionDecision(false, reason);
    }

    public static ActionDecision reject(String reason) {
        return reject(ResourceIds.parse(reason, "rejection reason"));
    }
}
