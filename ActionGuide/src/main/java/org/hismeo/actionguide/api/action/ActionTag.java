package org.hismeo.actionguide.api.action;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public record ActionTag(ResourceLocation value) implements Comparable<ActionTag> {
    public ActionTag {
        ResourceIds.require(value, "action tag");
    }

    public static ActionTag parse(String value) {
        return new ActionTag(ResourceIds.parse(value, "action tag"));
    }

    @Override
    public int compareTo(ActionTag other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
