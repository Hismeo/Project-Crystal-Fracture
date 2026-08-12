package org.hismeo.actionguide.api.cue;

import org.hismeo.actionguide.api.ResourceIds;

public record SectionId(String value) implements Comparable<SectionId> {
    public SectionId {
        value = ResourceIds.requireLocalId(value, "section id");
    }

    @Override
    public int compareTo(SectionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
