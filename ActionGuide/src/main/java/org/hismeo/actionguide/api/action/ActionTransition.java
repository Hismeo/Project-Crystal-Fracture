package org.hismeo.actionguide.api.action;

import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.cue.SectionId;

import java.util.Objects;
import java.util.Optional;

public record ActionTransition(
        SectionId fromSection,
        CueItemId inputState,
        ActionIntentId intent,
        Optional<SectionId> targetSection,
        Optional<ActionId> targetAction,
        TransitionMode mode
) {
    public ActionTransition {
        Objects.requireNonNull(fromSection, "from section");
        Objects.requireNonNull(inputState, "input state");
        Objects.requireNonNull(intent, "intent");
        targetSection = targetSection == null ? Optional.empty() : targetSection;
        targetAction = targetAction == null ? Optional.empty() : targetAction;
        Objects.requireNonNull(mode, "transition mode");
        if (targetSection.isPresent() == targetAction.isPresent()) {
            throw new IllegalArgumentException("transition must target exactly one section or action");
        }
    }
}
