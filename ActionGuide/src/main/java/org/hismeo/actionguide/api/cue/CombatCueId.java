package org.hismeo.actionguide.api.cue;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public record CombatCueId(ResourceLocation value) implements Comparable<CombatCueId> {
    public CombatCueId {
        ResourceIds.require(value, "combat cue id");
    }

    public static CombatCueId parse(String value) {
        return new CombatCueId(ResourceIds.parse(value, "combat cue id"));
    }

    @Override
    public int compareTo(CombatCueId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
