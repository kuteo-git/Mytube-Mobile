package com.mytube.app.ui

import com.mytube.app.ui.watch.fillFromPinch
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pinching the picture out to cover the screen, and back.
 *
 * The bug this answers is not in here and could not be: what shipped was the
 * *collapse drag* cropping the picture in fullscreen and the spring back
 * handing it straight over again — a fact about which number `VideoSurface`'s
 * `fill` was reading, which no unit seam reaches. What is here is the rule the
 * replacement turns on, and the reason it is worth a test of its own is that
 * both directions compile: a threshold the wrong way round is a gesture that
 * feels sticky, not anything that fails.
 */
class PinchToFillTest {

    @Test
    fun opening_the_fingers_fills_the_screen() {
        assertTrue(fillFromPinch(scale = 1.12f, filled = false))
        assertTrue(fillFromPinch(scale = 1.40f, filled = false))
    }

    @Test
    fun closing_them_gives_the_letterbox_back() {
        assertFalse(fillFromPinch(scale = 0.89f, filled = true))
        assertFalse(fillFromPinch(scale = 0.60f, filled = true))
    }

    /**
     * The whole point of the band between the thresholds. A pinch wanders, and
     * a picture that flips on every pixel of that wander is unusable — so
     * anything short of a decision leaves the state exactly where it was, in
     * both directions.
     */
    @Test
    fun a_wobble_decides_nothing() {
        assertFalse(fillFromPinch(scale = 1.00f, filled = false))
        assertTrue(fillFromPinch(scale = 1.00f, filled = true))
        assertFalse(fillFromPinch(scale = 1.11f, filled = false))
        assertTrue(fillFromPinch(scale = 0.90f, filled = true))
    }

    /**
     * And a gesture that runs the other way past a threshold it has already
     * crossed does not need to be told twice.
     */
    @Test
    fun a_decision_already_made_is_not_undone_by_more_of_the_same() {
        assertTrue(fillFromPinch(scale = 1.12f, filled = true))
        assertFalse(fillFromPinch(scale = 0.89f, filled = false))
    }
}
