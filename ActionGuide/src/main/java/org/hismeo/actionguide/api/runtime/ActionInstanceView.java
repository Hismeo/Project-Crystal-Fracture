package org.hismeo.actionguide.api.runtime;

import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.SectionId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ActionInstanceView(
        ActionInstanceId instanceId,
        ActionId actionId,
        CombatCueId cueId,
        ActionOwner owner,
        CueTime cursor,
        Optional<SectionId> currentSection,
        Set<CueItemId> activeStateIds,
        Set<CueItemId> consumedInputStateIds,
        ActionInstanceStatus status,
        Optional<ActionStopReason> stopReason,
        long definitionGeneration,
        long loopIteration
) {
    public ActionInstanceView {
        Objects.requireNonNull(instanceId, "instance id");
        Objects.requireNonNull(actionId, "action id");
        Objects.requireNonNull(cueId, "cue id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(cursor, "cursor");
        currentSection = currentSection == null ? Optional.empty() : currentSection;
        activeStateIds = Set.copyOf(activeStateIds);
        consumedInputStateIds = Set.copyOf(consumedInputStateIds);
        Objects.requireNonNull(status, "status");
        stopReason = stopReason == null ? Optional.empty() : stopReason;
    }
}
