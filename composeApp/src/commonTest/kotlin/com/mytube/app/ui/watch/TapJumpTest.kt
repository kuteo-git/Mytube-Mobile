package com.mytube.app.ui.watch

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a tap on the picture asks for: a jump, or the controls. */
class TapJumpTest {

    @Test
    fun aTapOnABarePictureShowsTheControls() {
        assertEquals(0.0, tapJump(running = false, double = false, forward = true))
    }

    @Test
    fun aDoubleTapJumpsTenSeconds() {
        assertEquals(10.0, tapJump(running = false, double = true, forward = true))
        assertEquals(-10.0, tapJump(running = false, double = true, forward = false))
    }

    /**
     * Reported from the phone: tapping three times to jump twenty seconds put
     * the controls up. Taps are paired into double taps, so the third was a
     * single one and toggled them. Inside a run every tap is ten more seconds.
     */
    @Test
    fun aSingleTapInsideARunJumpsRatherThanShowingTheControls() {
        assertEquals(10.0, tapJump(running = true, double = false, forward = true))
        assertEquals(-10.0, tapJump(running = true, double = false, forward = false))
    }

    /** Both taps of a pair count once the run has begun: four taps are thirty. */
    @Test
    fun aDoubleTapInsideARunIsTwoTaps() {
        assertEquals(20.0, tapJump(running = true, double = true, forward = true))
    }
}
