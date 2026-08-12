package org.hismeo.actionguide.api.runtime;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public record ActionStopReason(ResourceLocation value) {
    public static final ActionStopReason COMPLETED = parse("action_guide:completed");
    public static final ActionStopReason INTERRUPTED = parse("action_guide:interrupted");
    public static final ActionStopReason TRANSITIONED = parse("action_guide:transitioned");
    public static final ActionStopReason HANDLER_FAILURE = parse("action_guide:handler_failure");

    public ActionStopReason {
        ResourceIds.require(value, "stop reason");
    }

    public static ActionStopReason parse(String value) {
        return new ActionStopReason(ResourceIds.parse(value, "stop reason"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
