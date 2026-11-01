package com.olynk.waystonexppayment.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillAnimationTest {

    @Test
    void theFirstFrameShowsTheRealLevel() {
        FillAnimation animation = new FillAnimation();

        assertEquals(0.75F, animation.advance(0.75F, 1_000L), 1.0E-6F);
    }

    @Test
    void aFullerTankRisesTowardsItsLevel() {
        FillAnimation animation = new FillAnimation();
        animation.advance(0F, 0L);

        float shown = animation.advance(1F, 100L);

        assertTrue(shown > 0F && shown < 1F, "expected a level between empty and full, got " + shown);
    }

    @Test
    void halfASecondClosesMostOfTheGap() {
        FillAnimation animation = new FillAnimation();
        animation.advance(0F, 0L);

        float shown = animation.advance(1F, 500L);

        assertTrue(shown > 0.9F, "expected the surface to have nearly arrived, got " + shown);
    }

    @Test
    void anEmptiedTankFallsBackDown() {
        FillAnimation animation = new FillAnimation();
        animation.advance(1F, 0L);

        float shown = animation.advance(0F, 200L);

        assertTrue(shown > 0F && shown < 1F, "expected a falling level, got " + shown);
    }

    @Test
    void aChangeTooSmallToSeeSnapsImmediately() {
        FillAnimation animation = new FillAnimation();
        animation.advance(0.5F, 0L);

        assertEquals(0.5001F, animation.advance(0.5001F, 1L), 1.0E-6F);
    }

    @Test
    void aClockThatGoesBackwardsDoesNotMoveTheLevel() {
        FillAnimation animation = new FillAnimation();
        animation.advance(0F, 5_000L);

        assertEquals(0F, animation.advance(1F, 1_000L), 1.0E-6F);
    }

    @Test
    void aLevelOutsideZeroToOneIsClamped() {
        FillAnimation animation = new FillAnimation();

        assertEquals(1F, animation.advance(3F, 0L), 1.0E-6F);
        assertEquals(0F, new FillAnimation().advance(-2F, 0L), 1.0E-6F);
    }

    @Test
    void aLongPauseDoesNotOvershootTheTarget() {
        FillAnimation animation = new FillAnimation();
        animation.advance(0F, 0L);

        float shown = animation.advance(1F, 900_000L);

        assertTrue(shown <= 1F, "expected no overshoot, got " + shown);
        assertTrue(shown > 0.99F, "expected the pause to be caught up, got " + shown);
    }
}
