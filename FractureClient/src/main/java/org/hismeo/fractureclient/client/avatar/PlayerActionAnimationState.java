package org.hismeo.fractureclient.client.avatar;

import org.hismeo.actionguide.api.runtime.ActionInstanceId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Tracks the authoritative action instance currently driving each player's one-shot. */
final class PlayerActionAnimationState {
    private final Map<Integer, ActionInstanceId> dashInstances = new HashMap<>();

    synchronized void started(int actorEntityId, ActionInstanceId instanceId) {
        dashInstances.put(actorEntityId, Objects.requireNonNull(instanceId, "instanceId"));
    }

    synchronized boolean stopped(int actorEntityId, ActionInstanceId instanceId) {
        return dashInstances.remove(
                actorEntityId,
                Objects.requireNonNull(instanceId, "instanceId"));
    }

    synchronized void clear() {
        dashInstances.clear();
    }
}
