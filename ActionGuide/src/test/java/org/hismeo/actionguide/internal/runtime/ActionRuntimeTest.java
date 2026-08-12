package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.action.ActionAdmission;
import org.hismeo.actionguide.api.action.ActionContext;
import org.hismeo.actionguide.api.action.ActionDecision;
import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.action.ActionIntentId;
import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.action.IntentPhase;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueState;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.SectionId;
import org.hismeo.actionguide.api.event.CueStateHandler;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.hismeo.actionguide.api.runtime.ActionStopReason;
import org.hismeo.actionguide.internal.definition.ActionDefinitionLoader;
import org.hismeo.actionguide.internal.definition.ActionRegistry;
import org.hismeo.actionguide.internal.definition.CombatCueLoader;
import org.hismeo.actionguide.internal.definition.CueTypes;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionRuntimeTest {
    private static final ActionId ACTION_ID = ActionId.parse("test:sword_slash");
    private static final ActionIntentId LIGHT = ActionIntentId.parse("test:light_attack");
    private static final ActionIntentId COMBO = ActionIntentId.parse("test:combo");

    @Test
    void startsFromIdleCommitsOnceFindsShortAttackAndCompletes() {
        Fixture fixture = fixture(false);
        AtomicInteger commits = new AtomicInteger();
        List<String> windows = new ArrayList<>();
        fixture.runtime.registerAdmission(countingAdmission(commits, true));
        fixture.runtime.registerStateHandler(CueTypes.ATTACK, recordingHandler(windows));
        fixture.runtime.registerResolver((context, request) -> java.util.Optional.of(ACTION_ID));

        var result = fixture.runtime.submit(request(LIGHT, 1));
        assertTrue(result.accepted());
        assertEquals(1, commits.get());
        assertTrue(fixture.runtime.currentAction().isPresent());

        for (int tick = 0; tick < 5; tick++) {
            fixture.runtime.tick();
        }
        assertEquals(List.of("enter:attack_main", "exit:attack_main"), windows);
        assertFalse(fixture.runtime.isStateActive(CueTypes.ATTACK));

        for (int tick = 5; tick < 16; tick++) {
            fixture.runtime.tick();
        }
        assertTrue(fixture.runtime.currentAction().isEmpty());
        assertEquals(1, commits.get());
    }

    @Test
    void rejectedAdmissionNeverCommitsAndDuplicateSequenceIsRejected() {
        Fixture fixture = fixture(false);
        AtomicInteger commits = new AtomicInteger();
        fixture.runtime.registerAdmission(countingAdmission(commits, false));
        fixture.runtime.registerResolver((context, request) -> java.util.Optional.of(ACTION_ID));

        assertFalse(fixture.runtime.submit(request(LIGHT, 4)).accepted());
        assertEquals(0, commits.get());
        assertEquals(ActionRuntime.DUPLICATE_SEQUENCE, fixture.runtime.submit(request(LIGHT, 4)).reason());
    }

    @Test
    void interruptExitsEveryActiveStateAndClearsRuntime() {
        Fixture fixture = fixture(false);
        List<String> states = new ArrayList<>();
        fixture.runtime.registerStateHandler(CueTypes.SUPER_ARMOR, recordingHandler(states));
        fixture.runtime.start(ACTION_ID);
        for (int tick = 0; tick < 5; tick++) {
            fixture.runtime.tick();
        }
        assertTrue(fixture.runtime.isStateActive(CueTypes.SUPER_ARMOR));
        fixture.runtime.interrupt(ActionStopReason.INTERRUPTED);
        assertEquals(List.of("enter:armor", "exit:armor"), states);
        assertTrue(fixture.runtime.currentAction().isEmpty());
    }

    @Test
    void bufferedInputConsumesOneWindowAndDirectlyEntersTargetSection() {
        Fixture fixture = fixture(true);
        fixture.runtime.registerResolver((context, request) -> java.util.Optional.of(ACTION_ID));
        fixture.runtime.submit(request(LIGHT, 1));
        for (int tick = 0; tick < 8; tick++) {
            fixture.runtime.tick();
        }
        assertTrue(fixture.runtime.isInSection(new SectionId("active")));

        fixture.runtime.submit(request(COMBO, 2));
        var view = fixture.runtime.currentAction().orElseThrow();
        assertTrue(fixture.runtime.isInSection(new SectionId("recovery")));
        assertEquals(500_000, view.cursor().micros());
        assertTrue(view.consumedInputStateIds().stream().anyMatch(id -> id.value().equals("combo_window")));
        assertTrue(fixture.runtime.bufferedIntents().isEmpty());
    }

    @Test
    void handlerReentrantInterruptRunsAfterCurrentNotificationBatch() {
        Fixture fixture = fixture(false);
        List<String> windows = new ArrayList<>();
        fixture.runtime.registerStateHandler(CueTypes.ATTACK, new CueStateHandler() {
            @Override
            public void onEnter(ActionContext context, org.hismeo.actionguide.api.runtime.ActionInstanceView action, CueState state) {
                windows.add("enter");
                fixture.runtime.interrupt(ActionStopReason.INTERRUPTED);
            }

            @Override
            public void onExit(ActionContext context, org.hismeo.actionguide.api.runtime.ActionInstanceView action, CueState state) {
                windows.add("exit@" + action.cursor().micros());
            }
        });
        fixture.runtime.start(ACTION_ID);
        for (int tick = 0; tick < 5; tick++) {
            fixture.runtime.tick();
        }
        assertEquals(List.of("enter", "exit@210000"), windows);
        assertTrue(fixture.runtime.currentAction().isEmpty());
    }

    @Test
    void registryReloadDoesNotMutateRunningDefinitionSnapshot() {
        Fixture fixture = fixture(false);
        fixture.runtime.start(ACTION_ID);
        long originalGeneration = fixture.runtime.currentAction().orElseThrow().definitionGeneration();
        CombatCueDefinition shortCue = cueFrom(fixtureJson().replace("\"duration\": 0.8", "\"duration\": 0.8"));
        fixture.registry.publish(List.of(shortCue), List.of(fixture.action));
        assertNotEquals(originalGeneration, fixture.registry.snapshot().generation());

        for (int tick = 0; tick < 15; tick++) {
            fixture.runtime.tick();
        }
        assertTrue(fixture.runtime.currentAction().isPresent());
        assertEquals(originalGeneration, fixture.runtime.currentAction().orElseThrow().definitionGeneration());
        fixture.runtime.tick();
        assertTrue(fixture.runtime.currentAction().isEmpty());
    }

    @Test
    void holdEndPolicyKeepsTheAuthoritativeActionAtDuration() {
        CombatCueDefinition cue = cueFrom(fixtureJson());
        ActionDefinition action = new ActionDefinitionLoader().load(ACTION_ID, """
                {"cue":"test:sword_slash","entry_section":"startup","end_policy":"hold"}
                """);
        ActionRegistry registry = new ActionRegistry();
        registry.publish(List.of(cue), List.of(action));
        ActionRuntime runtime = new ActionRuntime(new ActionOwner(UUID.randomUUID()), registry);
        runtime.start(ACTION_ID);
        for (int tick = 0; tick < 20; tick++) {
            runtime.tick();
        }
        assertTrue(runtime.currentAction().isPresent());
        assertEquals(800_000, runtime.cursor().micros());
        assertTrue(runtime.currentAction().orElseThrow().activeStateIds().isEmpty());
    }

    private static ActionAdmission countingAdmission(AtomicInteger commits, boolean allow) {
        return new ActionAdmission() {
            @Override
            public ActionDecision evaluate(ActionContext context, ActionDefinition action) {
                return allow ? ActionDecision.allow() : ActionDecision.reject("test:denied");
            }

            @Override
            public void commit(ActionContext context, ActionDefinition action, ActionInstanceId instanceId) {
                commits.incrementAndGet();
            }
        };
    }

    private static CueStateHandler recordingHandler(List<String> output) {
        return new CueStateHandler() {
            @Override
            public void onEnter(ActionContext context, org.hismeo.actionguide.api.runtime.ActionInstanceView action, CueState state) {
                output.add("enter:" + state.id());
            }

            @Override
            public void onExit(ActionContext context, org.hismeo.actionguide.api.runtime.ActionInstanceView action, CueState state) {
                output.add("exit:" + state.id());
            }
        };
    }

    private static Fixture fixture(boolean withTransition) {
        CombatCueDefinition cue = cueFrom(fixtureJson());
        String transition = withTransition ? """
                ,"transitions":[{"from_section":"active","input_state":"combo_window","intent":"test:combo",
                                  "target_section":"recovery","mode":"direct"}]
                """ : "";
        String actionJson = """
                {"cue":"test:sword_slash","entry_section":"startup","channel":"full_body",
                 "tags":["test:melee"],"end_policy":"complete"%s}
                """.formatted(transition);
        ActionDefinition action = new ActionDefinitionLoader().load(ACTION_ID, actionJson);
        ActionRegistry registry = new ActionRegistry();
        registry.publish(List.of(cue), List.of(action));
        ActionRuntime runtime = new ActionRuntime(new ActionOwner(UUID.randomUUID()), registry);
        return new Fixture(registry, action, runtime);
    }

    private static CombatCueDefinition cueFrom(String json) {
        return new CombatCueLoader().load(CombatCueId.parse("test:sword_slash"), json).value();
    }

    private static String fixtureJson() {
        try (var stream = ActionRuntimeTest.class.getResourceAsStream("/fixtures/sword_slash.combat.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ActionIntentRequest request(ActionIntentId intent, long sequence) {
        return new ActionIntentRequest(intent, IntentPhase.PRESS, sequence, 0);
    }

    private record Fixture(ActionRegistry registry, ActionDefinition action, ActionRuntime runtime) {
    }
}
