package org.hismeo.actionguide.api.action;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public record AttackSlotId(ResourceLocation value) {
    public AttackSlotId {
        ResourceIds.require(value, "attack slot id");
    }

    public static AttackSlotId parse(String value) {
        return new AttackSlotId(ResourceIds.parse(value, "attack slot id"));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
