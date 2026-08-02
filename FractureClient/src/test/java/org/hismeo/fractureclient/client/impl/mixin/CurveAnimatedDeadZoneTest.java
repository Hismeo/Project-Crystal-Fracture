package org.hismeo.fractureclient.client.impl.mixin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurveAnimatedDeadZoneTest {
    private static final float TRANSITION_SECONDS = 0.35F;

    @Test
    void remainsStationaryInsideTheOuterBoundary() {
        CurveAnimatedDeadZone deadZone = new CurveAnimatedDeadZone();

        CurveAnimatedDeadZone.Position position = deadZone.update(
                0.0, 0.0, 0.0,
                CurveAnimatedDeadZone.OUTER_X - 0.01, 0.0, 0.0,
                TRANSITION_SECONDS, 0L
        );

        assertEquals(0.0, position.x(), 0.0);
        assertFalse(deadZone.isFollowingX());
    }

    @Test
    void keepsFollowingBetweenOuterAndInnerBoundaries() {
        CurveAnimatedDeadZone deadZone = new CurveAnimatedDeadZone();
        deadZone.update(0.0, 0.0, 0.0, 8.0, 0.0, 0.0,
                TRANSITION_SECONDS, 0L);
        assertFalse(deadZone.isFollowingX());

        deadZone.update(0.0, 0.0, 0.0, 12.0, 0.0, 0.0,
                TRANSITION_SECONDS, 10_000_000L);
        assertTrue(deadZone.isFollowingX());

        deadZone.update(0.0, 0.0, 0.0, 8.0, 0.0, 0.0,
                TRANSITION_SECONDS, 20_000_000L);
        assertTrue(deadZone.isFollowingX());
    }

    @Test
    void settlesAtTheInnerBoundaryAndStopsFollowing() {
        CurveAnimatedDeadZone deadZone = new CurveAnimatedDeadZone();
        CurveAnimatedDeadZone.Position position = deadZone.update(
                0.0, 0.0, 0.0, 12.0, 0.0, 0.0,
                TRANSITION_SECONDS, 0L
        );
        for (int frame = 1; frame <= 20; frame++) {
            position = deadZone.update(
                    0.0, 0.0, 0.0, 12.0, 0.0, 0.0,
                    TRANSITION_SECONDS, frame * 50_000_000L
            );
        }

        assertTrue(Math.abs(12.0 - position.x())
                <= CurveAnimatedDeadZone.INNER_X + 0.01);
        assertFalse(deadZone.isFollowingX());
    }

    @Test
    void snapsAfterTeleport() {
        CurveAnimatedDeadZone deadZone = new CurveAnimatedDeadZone();

        CurveAnimatedDeadZone.Position position = deadZone.update(
                0.0, 0.0, 0.0, 41.0, 5.0, -3.0,
                TRANSITION_SECONDS, 0L
        );

        assertEquals(41.0, position.x(), 0.0);
        assertEquals(5.0, position.y(), 0.0);
        assertEquals(-3.0, position.z(), 0.0);
        assertFalse(deadZone.isFollowingX());
        assertFalse(deadZone.isFollowingY());
        assertFalse(deadZone.isFollowingZ());
    }

    @Test
    void responseIsIndependentOfFrameStep() {
        CurveAnimatedDeadZone fastFrames = new CurveAnimatedDeadZone();
        CurveAnimatedDeadZone slowFrames = new CurveAnimatedDeadZone();
        CurveAnimatedDeadZone.Position fast = fastFrames.update(
                0.0, 0.0, 0.0, 12.0, 0.0, 0.0,
                TRANSITION_SECONDS, 0L
        );
        CurveAnimatedDeadZone.Position slow = slowFrames.update(
                0.0, 0.0, 0.0, 12.0, 0.0, 0.0,
                TRANSITION_SECONDS, 0L
        );

        for (int frame = 1; frame <= 30; frame++) {
            fast = fastFrames.update(
                    0.0, 0.0, 0.0, 12.0, 0.0, 0.0,
                    TRANSITION_SECONDS, frame * 10_000_000L
            );
        }
        for (int frame = 1; frame <= 6; frame++) {
            slow = slowFrames.update(
                    0.0, 0.0, 0.0, 12.0, 0.0, 0.0,
                    TRANSITION_SECONDS, frame * 50_000_000L
            );
        }

        assertEquals(fast.x(), slow.x(), 0.0001);
    }
}
