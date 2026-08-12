package org.hismeo.actionguide.api.network;

import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.SectionId;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.hismeo.actionguide.api.runtime.ActionStopReason;

import java.util.Optional;

public record ActionSnapshot(
        int actorEntityId,
        ActionInstanceId instanceId,
        ActionId actionId,
        CombatCueId cueId,
        Optional<SectionId> section,
        CueTime cursor,
        long serverTick,
        long sequence,
        Optional<ActionStopReason> stopReason
) {
    public ActionSnapshot {
        section = section == null ? Optional.empty() : section;
        stopReason = stopReason == null ? Optional.empty() : stopReason;
    }
}
