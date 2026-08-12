package org.hismeo.actionguide.api.network;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;

import java.util.Objects;
import java.util.Optional;

public sealed interface ActionSyncMessage permits ActionSyncMessage.Intent, ActionSyncMessage.Started,
        ActionSyncMessage.Transitioned, ActionSyncMessage.Stopped, ActionSyncMessage.Rejected,
        ActionSyncMessage.Snapshot, ActionSyncMessage.PresentationEvent {
    record Intent(ActionIntentRequest request) implements ActionSyncMessage {
    }

    record Started(ActionSnapshot snapshot) implements ActionSyncMessage {
    }

    record Transitioned(ActionSnapshot snapshot) implements ActionSyncMessage {
    }

    record Stopped(ActionSnapshot snapshot) implements ActionSyncMessage {
    }

    record Rejected(long requestSequence, ResourceLocation reason, Optional<ActionSnapshot> authority)
            implements ActionSyncMessage {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            authority = authority == null ? Optional.empty() : authority;
        }
    }

    record Snapshot(ActionSnapshot snapshot) implements ActionSyncMessage {
    }

    record PresentationEvent(int actorEntityId, ActionInstanceId instanceId, long loopIteration, CueEvent event)
            implements ActionSyncMessage {
    }
}
