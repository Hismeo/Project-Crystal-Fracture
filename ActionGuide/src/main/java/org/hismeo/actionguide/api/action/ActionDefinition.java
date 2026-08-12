package org.hismeo.actionguide.api.action;

import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.SectionId;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record ActionDefinition(
        ActionId id,
        CombatCueId cue,
        SectionId entrySection,
        ActionChannel channel,
        Set<ActionTag> tags,
        List<ActionTransition> transitions,
        ActionEndPolicy endPolicy
) {
    public ActionDefinition {
        Objects.requireNonNull(id, "action id");
        Objects.requireNonNull(cue, "cue id");
        Objects.requireNonNull(entrySection, "entry section");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(endPolicy, "end policy");
        tags = Set.copyOf(new TreeSet<>(tags));
        transitions = List.copyOf(transitions);
    }
}
