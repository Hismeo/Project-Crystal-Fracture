package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.cue.CueTime;

import java.util.List;

public record TimelineAdvance(CueTime cursor, long loopIteration, List<TimelineOperation> operations) {
    public TimelineAdvance {
        operations = List.copyOf(operations);
    }
}
