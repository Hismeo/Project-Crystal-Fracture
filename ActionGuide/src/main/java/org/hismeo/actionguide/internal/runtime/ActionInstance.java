package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.SectionId;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.hismeo.actionguide.api.runtime.ActionInstanceStatus;
import org.hismeo.actionguide.api.runtime.ActionInstanceView;
import org.hismeo.actionguide.api.runtime.ActionStopReason;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class ActionInstance {
    final ActionInstanceId instanceId;
    final ActionDefinition definition;
    final CombatCueDefinition cue;
    final ActionOwner owner;
    final long definitionGeneration;
    CueTime cursor;
    SectionId currentSection;
    final Set<CueItemId> activeStateIds = new LinkedHashSet<>();
    final Set<CueItemId> consumedInputStateIds = new LinkedHashSet<>();
    ActionInstanceStatus status = ActionInstanceStatus.STARTING;
    ActionStopReason stopReason;
    long loopIteration;

    ActionInstance(ActionInstanceId instanceId, ActionDefinition definition, CombatCueDefinition cue,
                   ActionOwner owner, long definitionGeneration) {
        this.instanceId = instanceId;
        this.definition = definition;
        this.cue = cue;
        this.owner = owner;
        this.definitionGeneration = definitionGeneration;
        this.cursor = cue.requireSection(definition.entrySection()).start();
        this.currentSection = definition.entrySection();
    }

    ActionInstanceView view() {
        return new ActionInstanceView(instanceId, definition.id(), cue.id(), owner, cursor,
                Optional.ofNullable(currentSection), activeStateIds, consumedInputStateIds, status,
                Optional.ofNullable(stopReason), definitionGeneration, loopIteration);
    }
}
