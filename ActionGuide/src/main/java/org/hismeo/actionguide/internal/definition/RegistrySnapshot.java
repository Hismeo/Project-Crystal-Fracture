package org.hismeo.actionguide.internal.definition;

import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CombatCueId;

import java.util.Map;
import java.util.Optional;

public record RegistrySnapshot(
        long generation,
        Map<CombatCueId, CombatCueDefinition> cues,
        Map<ActionId, ActionDefinition> actions
) {
    public RegistrySnapshot {
        cues = Map.copyOf(cues);
        actions = Map.copyOf(actions);
    }

    public Optional<CombatCueDefinition> cue(CombatCueId id) {
        return Optional.ofNullable(cues.get(id));
    }

    public Optional<ActionDefinition> action(ActionId id) {
        return Optional.ofNullable(actions.get(id));
    }
}
