package org.hismeo.actionguide.api.action;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public record InputWindowId(ResourceLocation value) {
    public InputWindowId {
        ResourceIds.require(value, "input window id");
    }

    public static InputWindowId parse(String value) {
        return new InputWindowId(ResourceIds.parse(value, "input window id"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
