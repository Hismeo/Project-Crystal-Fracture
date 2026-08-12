package org.hismeo.actionguide.internal.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.ActionTransition;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.cue.CueSection;
import org.hismeo.actionguide.api.cue.CueState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DefinitionValidator {
    public List<DefinitionProblem> validate(CombatCueDefinition cue) {
        List<DefinitionProblem> problems = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (int index = 0; index < cue.sections().size(); index++) {
            CueSection section = cue.sections().get(index);
            unique(ids, section.id().value(), "$.sections[" + index + "].id", problems);
            if (section.end().compareTo(cue.duration()) > 0) {
                error(problems, "$.sections[" + index + "].end", "section ends after duration");
            }
            if (index > 0) {
                CueSection previous = cue.sections().get(index - 1);
                int comparison = section.start().compareTo(previous.end());
                if (comparison < 0) {
                    error(problems, "$.sections[" + index + "].start", "sections " + previous.id() + " and " + section.id() + " overlap");
                } else if (comparison > 0) {
                    warning(problems, "$.sections[" + index + "].start", "sections " + previous.id() + " and " + section.id() + " have a gap");
                }
            }
        }

        for (int index = 0; index < cue.events().size(); index++) {
            CueEvent event = cue.events().get(index);
            String path = "$.events[" + index + "]";
            unique(ids, event.id().value(), path + ".id", problems);
            if (event.time().compareTo(cue.duration()) > 0) {
                error(problems, path + ".time", "event is after duration");
            }
            validateEventPayload(event, path, problems);
        }

        for (int index = 0; index < cue.states().size(); index++) {
            CueState state = cue.states().get(index);
            String path = "$.states[" + index + "]";
            unique(ids, state.id().value(), path + ".id", problems);
            if (state.end().compareTo(cue.duration()) > 0) {
                error(problems, path + ".end", "state ends after duration");
            }
            if ((state.type().equals(CueTypes.ATTACK) || state.type().equals(CueTypes.INPUT)) && !hasText(state.payload(), "slot")) {
                error(problems, path + ".payload.slot", (state.type().equals(CueTypes.ATTACK) ? "attack" : "input") + " state requires slot");
            }
            if ((state.type().equals(CueTypes.ATTACK) || state.type().equals(CueTypes.INPUT)) && !cue.sections().isEmpty()) {
                boolean contained = cue.sections().stream().anyMatch(section ->
                        section.start().compareTo(state.start()) <= 0 && state.end().compareTo(section.end()) <= 0);
                if (!contained) {
                    warning(problems, path, "state " + state.id() + " crosses a section boundary");
                }
            }
        }
        return List.copyOf(problems);
    }

    public List<DefinitionProblem> validate(ActionDefinition action, Map<org.hismeo.actionguide.api.cue.CombatCueId, CombatCueDefinition> cues) {
        List<DefinitionProblem> problems = new ArrayList<>();
        CombatCueDefinition cue = cues.get(action.cue());
        if (cue == null) {
            error(problems, "$.cue", "unknown combat cue " + action.cue());
            return problems;
        }
        Set<org.hismeo.actionguide.api.cue.SectionId> sections = cue.sections().stream().map(CueSection::id).collect(Collectors.toSet());
        Map<org.hismeo.actionguide.api.cue.CueItemId, CueState> states = cue.states().stream().collect(Collectors.toMap(CueState::id, Function.identity()));
        if (!sections.contains(action.entrySection())) {
            error(problems, "$.entry_section", "unknown entry section " + action.entrySection());
        }
        for (int index = 0; index < action.transitions().size(); index++) {
            ActionTransition transition = action.transitions().get(index);
            String path = "$.transitions[" + index + "]";
            if (!sections.contains(transition.fromSection())) {
                error(problems, path + ".from_section", "unknown section " + transition.fromSection());
            }
            CueState inputState = states.get(transition.inputState());
            if (inputState == null || !inputState.type().equals(CueTypes.INPUT)) {
                error(problems, path + ".input_state", "unknown input state " + transition.inputState());
            }
            transition.targetSection().filter(target -> !sections.contains(target)).ifPresent(target ->
                    error(problems, path + ".target_section", "unknown target section " + target));
        }
        return List.copyOf(problems);
    }

    private static void validateEventPayload(CueEvent event, String path, List<DefinitionProblem> problems) {
        JsonObject payload = event.payload();
        if (event.type().equals(CueTypes.SOUND) && !hasText(payload, "cue")) {
            error(problems, path + ".payload.cue", "sound event requires cue");
        } else if (event.type().equals(CueTypes.VFX) && !hasText(payload, "effect")) {
            error(problems, path + ".payload.effect", "vfx event requires effect");
        } else if (event.type().equals(CueTypes.PROJECTILE) && !hasText(payload, "slot")) {
            error(problems, path + ".payload.slot", "projectile event requires slot");
        } else if (event.type().equals(CueTypes.CAMERA_SHAKE)) {
            for (String field : List.of("strength", "duration", "frequency")) {
                JsonElement value = payload.get(field);
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || value.getAsDouble() < 0) {
                    error(problems, path + ".payload." + field, "camera shake " + field + " must be non-negative");
                }
            }
        }
    }

    private static boolean hasText(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() && !value.getAsString().trim().isEmpty();
    }

    private static void unique(Set<String> ids, String id, String path, List<DefinitionProblem> problems) {
        if (!ids.add(id)) {
            error(problems, path, "duplicate cue item id " + id);
        }
    }

    static void error(List<DefinitionProblem> problems, String path, String message) {
        problems.add(new DefinitionProblem(ProblemSeverity.ERROR, path, message));
    }

    static void warning(List<DefinitionProblem> problems, String path, String message) {
        problems.add(new DefinitionProblem(ProblemSeverity.WARNING, path, message));
    }
}
