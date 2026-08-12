package org.hismeo.actionguide.internal.network;

import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.network.ActionSnapshot;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Ordering and presentation-event deduplication for the neutral client sync stream. */
public final class ActionClientSyncState {
    private final Map<Integer, ActorState> actors = new HashMap<>();

    public synchronized boolean acceptSnapshot(ActionSnapshot snapshot) {
        ActorState state = actors.get(snapshot.actorEntityId());
        if (state != null) {
            int instanceOrder = snapshot.instanceId().compareTo(state.instanceId);
            if (instanceOrder < 0 || instanceOrder == 0 && snapshot.sequence() <= state.sequence) {
                return false;
            }
        }
        if (state == null || !state.instanceId.equals(snapshot.instanceId())) {
            state = new ActorState(snapshot.instanceId(), snapshot.sequence());
            actors.put(snapshot.actorEntityId(), state);
        } else {
            state.sequence = snapshot.sequence();
        }
        state.stopped = snapshot.stopReason().isPresent();
        return true;
    }

    public synchronized boolean acceptEvent(int actorEntityId, ActionInstanceId instanceId, long loopIteration,
                                            CueItemId eventId) {
        ActorState state = actors.get(actorEntityId);
        if (state != null && (instanceId.compareTo(state.instanceId) < 0
                || instanceId.equals(state.instanceId) && state.stopped)) {
            return false;
        }
        if (state == null || !state.instanceId.equals(instanceId)) {
            state = new ActorState(instanceId, -1);
            actors.put(actorEntityId, state);
        }
        return state.events.add(new EventKey(loopIteration, eventId));
    }

    private static final class ActorState {
        private final ActionInstanceId instanceId;
        private long sequence;
        private boolean stopped;
        private final Set<EventKey> events = new HashSet<>();

        private ActorState(ActionInstanceId instanceId, long sequence) {
            this.instanceId = instanceId;
            this.sequence = sequence;
        }
    }

    private record EventKey(long loopIteration, CueItemId eventId) {
    }
}
