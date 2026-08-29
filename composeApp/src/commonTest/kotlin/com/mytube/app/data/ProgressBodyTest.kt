package com.mytube.app.data

import com.mytube.app.data.remote.wholeSeconds
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The gateway's `positionSeconds` is an int32.
 *
 * This test exists because sending a fraction there is not a rounding
 * difference: Go refuses the entire body with 400, the app swallowed it as a
 * failed report, and the position on the server stayed where it had been before
 * the app was ever opened. Nothing failed visibly for as long as it took to
 * compare the two numbers by hand.
 */
class ProgressBodyTest {

    @Test
    fun roundsToWholeSeconds() {
        assertEquals(20L, wholeSeconds(20.4))
        assertEquals(21L, wholeSeconds(20.6))
    }

    @Test
    fun refusesNegatives() {
        // A player reports a position before it has loaded, and on some it is
        // negative. That is not a place in a video and must not be stored.
        assertEquals(0L, wholeSeconds(-0.5))
    }

    @Test
    fun keepsZero() {
        assertEquals(0L, wholeSeconds(0.0))
    }
}
