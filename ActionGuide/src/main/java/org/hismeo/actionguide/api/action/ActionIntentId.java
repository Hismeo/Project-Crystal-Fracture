package org.hismeo.actionguide.api.action;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public record ActionIntentId(ResourceLocation value) implements Comparable<ActionIntentId> {
    public ActionIntentId {
        ResourceIds.require(value, "action intent id");
    }

    public static ActionIntentId parse(String value) {
        return new ActionIntentId(ResourceIds.parse(value, "action intent id"));
    }

    @Override
    public int compareTo(ActionIntentId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
