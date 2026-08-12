package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.cue.CueSection;
import org.hismeo.actionguide.api.cue.CueState;
import org.hismeo.actionguide.api.cue.CueTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TimelineStepper {
    private static final Comparator<Scheduled> SCHEDULED_ORDER = Comparator.comparingLong(Scheduled::offset)
            .thenComparingInt(value -> rank(value.operation()))
            .thenComparingLong(value -> value.operation().loopIteration())
            .thenComparingInt(value -> eventOrder(value.operation()))
            .thenComparing(value -> itemId(value.operation()));

    public List<TimelineOperation> enterAt(CombatCueDefinition cue, CueTime time, long loopIteration) {
        List<TimelineOperation> operations = new ArrayList<>();
        cue.sectionAt(time).ifPresent(section -> operations.add(new TimelineOperation.SectionEnter(time, loopIteration, section)));
        cue.states().stream().filter(state -> state.activeAt(time))
                .forEach(state -> operations.add(new TimelineOperation.StateEnter(time, loopIteration, state)));
        cue.events().stream().filter(event -> event.time().equals(time))
                .forEach(event -> operations.add(new TimelineOperation.Event(time, loopIteration, event)));
        operations.sort(operationComparator());
        return List.copyOf(operations);
    }

    public TimelineAdvance advance(CombatCueDefinition cue, CueTime previous, long loopIteration, CueTime delta) {
        if (previous.compareTo(cue.duration()) > 0) {
            throw new IllegalArgumentException("cursor is after cue duration");
        }
        if (delta.equals(CueTime.ZERO)) {
            return new TimelineAdvance(previous, loopIteration, List.of());
        }
        List<Scheduled> scheduled = new ArrayList<>();
        long remaining = delta.micros();
        long offset = 0;
        CueTime cursor = previous;
        long iteration = loopIteration;

        while (remaining > 0) {
            long toEnd = cue.duration().micros() - cursor.micros();
            if (!cue.loop()) {
                long step = Math.min(remaining, toEnd);
                collectBetween(cue, cursor, new CueTime(cursor.micros() + step), iteration, offset, scheduled);
                cursor = new CueTime(cursor.micros() + step);
                remaining -= step;
                if (cursor.equals(cue.duration())) {
                    scheduled.add(new Scheduled(offset + step, new TimelineOperation.Complete(cursor, iteration)));
                    remaining = 0;
                }
                break;
            }

            if (toEnd == 0) {
                iteration++;
                cursor = CueTime.ZERO;
                addEntryAtZero(cue, iteration, offset, scheduled);
                continue;
            }
            long step = Math.min(remaining, toEnd);
            CueTime next = new CueTime(cursor.micros() + step);
            collectBetween(cue, cursor, next, iteration, offset, scheduled);
            cursor = next;
            remaining -= step;
            offset += step;
            if (cursor.equals(cue.duration())) {
                iteration++;
                cursor = CueTime.ZERO;
                addEntryAtZero(cue, iteration, offset, scheduled);
            }
        }
        scheduled.sort(SCHEDULED_ORDER);
        return new TimelineAdvance(cursor, iteration, scheduled.stream().map(Scheduled::operation).toList());
    }

    private static void collectBetween(CombatCueDefinition cue, CueTime previous, CueTime current, long iteration,
                                       long baseOffset, List<Scheduled> result) {
        cue.states().stream().filter(state -> crossed(previous, current, state.end()))
                .forEach(state -> result.add(scheduled(baseOffset, previous, new TimelineOperation.StateExit(state.end(), iteration, state))));
        cue.sections().stream().filter(section -> crossed(previous, current, section.end()))
                .forEach(section -> result.add(scheduled(baseOffset, previous, new TimelineOperation.SectionExit(section.end(), iteration, section))));
        cue.sections().stream().filter(section -> crossed(previous, current, section.start()))
                .forEach(section -> result.add(scheduled(baseOffset, previous, new TimelineOperation.SectionEnter(section.start(), iteration, section))));
        cue.states().stream().filter(state -> crossed(previous, current, state.start()))
                .forEach(state -> result.add(scheduled(baseOffset, previous, new TimelineOperation.StateEnter(state.start(), iteration, state))));
        cue.events().stream().filter(event -> crossed(previous, current, event.time()))
                .forEach(event -> result.add(scheduled(baseOffset, previous, new TimelineOperation.Event(event.time(), iteration, event))));
    }

    private static void addEntryAtZero(CombatCueDefinition cue, long iteration, long offset, List<Scheduled> result) {
        cue.sectionAt(CueTime.ZERO).ifPresent(section -> result.add(new Scheduled(offset,
                new TimelineOperation.SectionEnter(CueTime.ZERO, iteration, section))));
        cue.states().stream().filter(state -> state.start().equals(CueTime.ZERO))
                .forEach(state -> result.add(new Scheduled(offset, new TimelineOperation.StateEnter(CueTime.ZERO, iteration, state))));
        cue.events().stream().filter(event -> event.time().equals(CueTime.ZERO))
                .forEach(event -> result.add(new Scheduled(offset, new TimelineOperation.Event(CueTime.ZERO, iteration, event))));
    }

    private static Scheduled scheduled(long baseOffset, CueTime previous, TimelineOperation operation) {
        return new Scheduled(baseOffset + operation.time().micros() - previous.micros(), operation);
    }

    private static boolean crossed(CueTime previous, CueTime current, CueTime boundary) {
        return previous.compareTo(boundary) < 0 && boundary.compareTo(current) <= 0;
    }

    private static Comparator<TimelineOperation> operationComparator() {
        return Comparator.comparingInt(TimelineStepper::rank)
                .thenComparingInt(TimelineStepper::eventOrder)
                .thenComparing(TimelineStepper::itemId);
    }

    private static int rank(TimelineOperation operation) {
        return switch (operation) {
            case TimelineOperation.StateExit ignored -> 0;
            case TimelineOperation.SectionExit ignored -> 1;
            case TimelineOperation.SectionEnter ignored -> 2;
            case TimelineOperation.StateEnter ignored -> 3;
            case TimelineOperation.Event ignored -> 4;
            case TimelineOperation.Complete ignored -> 5;
        };
    }

    private static String itemId(TimelineOperation operation) {
        return switch (operation) {
            case TimelineOperation.StateExit value -> value.state().id().value();
            case TimelineOperation.SectionExit value -> value.section().id().value();
            case TimelineOperation.SectionEnter value -> value.section().id().value();
            case TimelineOperation.StateEnter value -> value.state().id().value();
            case TimelineOperation.Event value -> value.event().id().value();
            case TimelineOperation.Complete ignored -> "";
        };
    }

    private static int eventOrder(TimelineOperation operation) {
        return operation instanceof TimelineOperation.Event value ? value.event().order() : 0;
    }

    private record Scheduled(long offset, TimelineOperation operation) {
    }
}
