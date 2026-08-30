package com.mytube.app.ui.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The drag that puts the video into the miniplayer.
 *
 * The rule the project settled on is that a Preview is enough for UI, so what is
 * worth a test is the part that is not visual: given a distance, a picture and a
 * speed, is this a commit. Driving a real drag would test Compose's gesture
 * detector, which is not this app's code.
 *
 * The numbers are the web app's, read across from `player-gesture.ts` rather
 * than invented here — one gesture on two clients should not need learning
 * twice.
 */
class SwipeToDismissTest {

    @Test
    fun dragUpIsIgnored() {
        // The gesture is one-way. Dragging up must not lift the screen off the
        // top of the display, which is what an unclamped offset would do.
        assertEquals(0f, dragOffset(-200f))
    }

    @Test
    fun dragDownFollowsTheFinger() {
        assertEquals(200f, dragOffset(200f))
    }

    /**
     * Progress runs against the journey, not against the picture.
     *
     * This was the web's own first mistake and it is kept as a test because it
     * is invisible in the code and unmistakable on a phone: a 220px picture
     * against a 640px journey would reach the bar after a third of the
     * movement, and the video would arrive while the finger was still
     * mid-screen — the player fleeing the hand dragging it.
     */
    @Test
    fun progressRunsAgainstTheDistanceToTheBar() {
        val travel = 640f
        assertEquals(0f, travelProgress(0f, travel))
        assertEquals(0.5f, travelProgress(320f, travel))
        assertEquals(1f, travelProgress(640f, travel))
        // Past the bar is still the bar.
        assertEquals(1f, travelProgress(900f, travel))
    }

    @Test
    fun neverDividesByAnUnmeasuredJourney() {
        // onSizeChanged has not fired yet. Without the guard the first frame of
        // the first drag is either a crash or an instant dismissal.
        assertEquals(0f, travelProgress(100f, 0f))
    }

    @Test
    fun shortDragSpringsBack() {
        // Under a quarter of the picture. This is the case that matters most:
        // it is the accidental drag, and getting it wrong throws away the video
        // somebody was watching.
        assertFalse(shouldCommit(dy = 54f, playerHeight = 220f, velocity = 0f))
    }

    @Test
    fun aQuarterOfThePictureCommits() {
        assertTrue(shouldCommit(dy = 55f, playerHeight = 220f, velocity = 0f))
    }

    /**
     * A flick commits without travelling far.
     *
     * Without this a fast, short movement springs back, which reads as the
     * gesture having been missed rather than declined.
     */
    @Test
    fun aFastFlickCommitsFromAnywhere() {
        assertTrue(shouldCommit(dy = 20f, playerHeight = 220f, velocity = COMMIT_VELOCITY))
        assertFalse(shouldCommit(dy = 20f, playerHeight = 220f, velocity = COMMIT_VELOCITY - 1f))
    }

    @Test
    fun neverCommitsOnDistanceBeforeThePictureIsMeasured() {
        assertFalse(shouldCommit(dy = 5000f, playerHeight = 0f, velocity = 0f))
    }

    @Test
    fun velocityIsPixelsPerSecond() {
        assertEquals(1000f, velocityOf(100f, 100L))
        assertEquals(0f, velocityOf(100f, 0L))
    }

    /** Linear: an eased value runs ahead of the finger dragging it. */
    @Test
    fun thePictureShrinksInStepWithTheDrag() {
        assertEquals(1f, lerp(1f, 0.3f, 0f))
        assertEquals(0.65f, lerp(1f, 0.3f, 0.5f))
        assertEquals(0.3f, lerp(1f, 0.3f, 1f))
        // Out of range is clamped rather than extrapolated.
        assertEquals(0.3f, lerp(1f, 0.3f, 2f))
    }
}
