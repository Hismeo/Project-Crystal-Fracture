package org.hismeo.actionguide.api.cue;

import java.util.Objects;

public record CueSection(SectionId id, CueTime start, CueTime end) {
    public CueSection {
        Objects.requireNonNull(id, "section id");
        Objects.requireNonNull(start, "section start");
        Objects.requireNonNull(end, "section end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("section " + id + " must use a valid [start, end) interval");
        }
    }

    public boolean contains(CueTime time) {
        return start.isBeforeOrEqual(time) && time.isBefore(end);
    }
}
