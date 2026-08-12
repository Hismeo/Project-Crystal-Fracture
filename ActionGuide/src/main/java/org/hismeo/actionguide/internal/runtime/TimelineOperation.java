package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.cue.CueSection;
import org.hismeo.actionguide.api.cue.CueState;
import org.hismeo.actionguide.api.cue.CueTime;

public sealed interface TimelineOperation permits TimelineOperation.StateExit, TimelineOperation.SectionExit,
        TimelineOperation.SectionEnter, TimelineOperation.StateEnter, TimelineOperation.Event, TimelineOperation.Complete {
    CueTime time();

    long loopIteration();

    record StateExit(CueTime time, long loopIteration, CueState state) implements TimelineOperation {
    }

    record SectionExit(CueTime time, long loopIteration, CueSection section) implements TimelineOperation {
    }

    record SectionEnter(CueTime time, long loopIteration, CueSection section) implements TimelineOperation {
    }

    record StateEnter(CueTime time, long loopIteration, CueState state) implements TimelineOperation {
    }

    record Event(CueTime time, long loopIteration, CueEvent event) implements TimelineOperation {
    }

    record Complete(CueTime time, long loopIteration) implements TimelineOperation {
    }
}
