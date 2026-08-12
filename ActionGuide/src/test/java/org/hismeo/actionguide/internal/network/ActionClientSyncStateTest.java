package org.hismeo.actionguide.internal.network;

import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.network.ActionSnapshot;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.hismeo.actionguide.api.runtime.ActionStopReason;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionClientSyncStateTest {
    @Test
    void rejectsOldSnapshotsAndDeduplicatesPresentationEvents() {
        ActionClientSyncState state = new ActionClientSyncState();
        assertTrue(state.acceptSnapshot(snapshot(2, 10, Optional.empty())));
        assertFalse(state.acceptSnapshot(snapshot(2, 10, Optional.empty())));
        assertFalse(state.acceptSnapshot(snapshot(1, 11, Optional.empty())));

        CueItemId event = new CueItemId("swing_sound");
        assertTrue(state.acceptEvent(7, new ActionInstanceId(2), 0, event));
        assertFalse(state.acceptEvent(7, new ActionInstanceId(2), 0, event));
        assertTrue(state.acceptEvent(7, new ActionInstanceId(2), 1, event));

        assertTrue(state.acceptSnapshot(snapshot(2, 12, Optional.of(ActionStopReason.COMPLETED))));
        assertFalse(state.acceptEvent(7, new ActionInstanceId(2), 2, event));
        assertTrue(state.acceptSnapshot(snapshot(3, 1, Optional.empty())));
    }

    private static ActionSnapshot snapshot(long instance, long sequence, Optional<ActionStopReason> stopReason) {
        return new ActionSnapshot(7, new ActionInstanceId(instance), ActionId.parse("test:slash"),
                CombatCueId.parse("test:slash"), Optional.empty(), CueTime.ZERO, 20, sequence, stopReason);
    }
}
