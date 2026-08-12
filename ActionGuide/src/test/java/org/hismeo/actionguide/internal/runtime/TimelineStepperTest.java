package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.internal.definition.CombatCueLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TimelineStepperTest {
    private final TimelineStepper stepper = new TimelineStepper();

    @Test
    void entryActivatesSectionStateThenEvent() {
        CombatCueDefinition cue = loadFixture();
        var operations = stepper.enterAt(cue, CueTime.ZERO, 0);
        assertInstanceOf(TimelineOperation.SectionEnter.class, operations.get(0));
        assertInstanceOf(TimelineOperation.Event.class, operations.get(1));
        assertEquals("start", ((TimelineOperation.Event) operations.get(1)).event().id().value());
    }

    @Test
    void largeStepFindsShortStateAndUsesFrozenBoundaryOrder() {
        CombatCueDefinition cue = loadFixture();
        var advance = stepper.advance(cue, CueTime.fromSeconds("0.19"), 0, CueTime.fromSeconds("0.31"));
        assertEquals(500_000, advance.cursor().micros());
                assertEquals(java.util.List.of(
                        "section-exit:startup", "section-enter:active", "state-enter:armor",
                        "event:swing_sound", "event:swing_vfx", "state-enter:attack_main",
                        "state-exit:attack_main", "state-enter:combo_window", "state-exit:armor",
                        "state-exit:combo_window", "section-exit:active", "section-enter:recovery"),
                advance.operations().stream().map(TimelineStepperTest::describe).toList());
    }

    @Test
    void directSectionEntryActivatesNewStateBeforeOrderedEntryEvents() {
        CombatCueDefinition cue = loadFixture();
        var operations = stepper.enterAt(cue, CueTime.fromSeconds("0.2"), 0);
        assertEquals(java.util.List.of("section-enter:active", "state-enter:armor",
                        "event:swing_sound", "event:swing_vfx"),
                operations.stream().map(TimelineStepperTest::describe).toList());
    }

    @Test
    void loopingAcrossTailKeepsTailBeforeNextIterationStart() {
        String json = """
                {"schema_version":4,"duration":1,"skeleton":{"id":"game:test","version":1},
                 "sections":[{"id":"all","start":0,"end":1}],
                 "events":[{"id":"tail","time":0.95,"type":"test:tail","payload":{}},
                           {"id":"start","time":0,"type":"test:start","payload":{}}],
                 "states":[],"root_motion":{"enabled":false,"mode":"xz_yaw"},"loop":true}
                """;
        CombatCueDefinition cue = new CombatCueLoader().load(CombatCueId.parse("test:loop"), json).value();
        var advance = stepper.advance(cue, CueTime.fromSeconds("0.9"), 0, CueTime.fromSeconds("0.15"));
        assertEquals(java.util.List.of("event:tail", "section-exit:all", "section-enter:all", "event:start"),
                advance.operations().stream().map(TimelineStepperTest::describe).toList());
        assertEquals(50_000, advance.cursor().micros());
        assertEquals(1, advance.loopIteration());
    }

    private CombatCueDefinition loadFixture() {
        return new CombatCueLoader().load(CombatCueId.parse("test:sword"), new InputStreamReader(
                getClass().getResourceAsStream("/fixtures/sword_slash.combat.json"), StandardCharsets.UTF_8)).value();
    }

    private static String describe(TimelineOperation operation) {
        return switch (operation) {
            case TimelineOperation.StateExit value -> "state-exit:" + value.state().id();
            case TimelineOperation.SectionExit value -> "section-exit:" + value.section().id();
            case TimelineOperation.SectionEnter value -> "section-enter:" + value.section().id();
            case TimelineOperation.StateEnter value -> "state-enter:" + value.state().id();
            case TimelineOperation.Event value -> "event:" + value.event().id();
            case TimelineOperation.Complete ignored -> "complete";
        };
    }
}
