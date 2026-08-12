package org.hismeo.actionguide.api.action;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public record ActionId(ResourceLocation value) implements Comparable<ActionId> {
    public ActionId {
        ResourceIds.require(value, "action id");
    }

    public static ActionId parse(String value) {
        return new ActionId(ResourceIds.parse(value, "action id"));
    }

    @Override
    public int compareTo(ActionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
