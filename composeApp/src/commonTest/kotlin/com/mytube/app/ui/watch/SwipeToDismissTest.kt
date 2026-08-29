package com.mytube.app.ui.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dismiss decision, tested here rather than through the gesture.
 *
 * The rule the project settled on is that a Preview is enough for UI, so what is
 * worth a test is the part that is not visual: given a distance and a screen, is
 * this a dismiss. Driving a real drag would test Compose's gesture detector,
 * which is not this app's code.
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

    @Test
    fun shortDragSpringsBack() {
        // A quarter of the screen, which is under the third the threshold asks
        // for. This is the case that matters most: it is the accidental drag,
        // and getting it wrong throws away the video somebody was watching.
        assertFalse(shouldDismiss(offset = 500f, screenHeight = 2000f))
    }

    @Test
    fun longDragDismisses() {
        assertTrue(shouldDismiss(offset = 700f, screenHeight = 2000f))
    }

    @Test
    fun neverDismissesBeforeTheScreenIsMeasured() {
        // onSizeChanged has not fired yet, so the height is zero and every
        // comparison against a fraction of it would be true. Without the guard,
        // the first pixel of the first drag closes the screen.
        assertFalse(shouldDismiss(offset = 1f, screenHeight = 0f))
    }
}
