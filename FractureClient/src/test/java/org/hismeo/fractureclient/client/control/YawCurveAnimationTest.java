package org.hismeo.fractureclient.client.control;

import com.kaleblangley.haikalat.core.curve.Curves;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YawCurveAnimationTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void crossesDegreeWrapOnTheShortestPath() {
        YawCurveAnimation animation = new YawCurveAnimation(Curves.EASE_OUT_CUBIC);
        animation.snap(170.0F, 0L);
        animation.retarget(-170.0F, 1.0F, 0L);

        float halfway = animation.sample(SECOND / 2L);

        assertTrue(YawCurveAnimation.angleDelta(halfway, 170.0F) > 0.0F);
        assertTrue(Math.abs(YawCurveAnimation.angleDelta(-170.0F, halfway)) < 20.0F);
        assertEquals(-170.0F, animation.sample(SECOND), 0.0001F);
    }

    @Test
    void retargetStartsAtTheCurrentlyVisibleAngle() {
        YawCurveAnimation animation = new YawCurveAnimation(Curves.EASE_IN_OUT_CUBIC);
        animation.snap(0.0F, 0L);
        animation.retarget(90.0F, 1.0F, 0L);
        long retargetTime = 400_000_000L;
        float visibleBeforeRetarget = animation.sample(retargetTime);

        animation.retarget(180.0F, 1.0F, retargetTime);

        assertEquals(visibleBeforeRetarget, animation.sample(retargetTime), 0.0001F);
        assertEquals(-180.0F, animation.sample(retargetTime + SECOND), 0.0001F);
    }
}
