package org.hismeo.fractureclient.client.avatar;

import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayerActionAnimationStateTest {
    @Test
    void staleStopCannotCancelNewerDashInstance() {
        PlayerActionAnimationState state = new PlayerActionAnimationState();
        ActionInstanceId oldDash = new ActionInstanceId(11);
        ActionInstanceId newDash = new ActionInstanceId(12);

        state.started(7, oldDash);
        state.started(7, newDash);

        assertFalse(state.stopped(7, oldDash));
        assertTrue(state.stopped(7, newDash));
        assertFalse(state.stopped(7, newDash));
    }
}
