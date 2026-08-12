package org.hismeo.actionguide.api.cue;

import org.hismeo.actionguide.api.ResourceIds;

public record CueItemId(String value) implements Comparable<CueItemId> {
    public CueItemId {
        value = ResourceIds.requireLocalId(value, "cue item id");
    }

    @Override
    public int compareTo(CueItemId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
