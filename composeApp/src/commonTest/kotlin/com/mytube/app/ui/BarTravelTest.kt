package com.mytube.app.ui

import com.mytube.app.ui.shell.barTravel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bars get out of the way, and the two of them do not answer the same
 * question.
 *
 * The case this file exists for is the third one: **the top bar leaves even
 * when the bottom bar is only collapsing.** That was the shipped bug — one
 * float served both bars, so anything playing pinned the chip row as well, and
 * a reader scrolling a feed kept two bands of chrome for ever.
 */
class BarTravelTest {

    @Test
    fun `at the top nothing moves`() {
        val t = barTravel(barsShowing = true, playerRestsOnBar = false)
        assertFalse(t.topHidden)
        assertFalse(t.bottomHidden)
        assertFalse(t.bottomCollapsed)
    }

    @Test
    fun `at the top nothing moves even with a player on the bar`() {
        assertEquals(
            barTravel(barsShowing = true, playerRestsOnBar = false),
            barTravel(barsShowing = true, playerRestsOnBar = true),
        )
    }

    @Test
    fun `scrolling down with nothing playing takes both bars away`() {
        val t = barTravel(barsShowing = false, playerRestsOnBar = false)
        assertTrue(t.topHidden)
        assertTrue(t.bottomHidden)
        assertFalse(t.bottomCollapsed)
    }

    @Test
    fun `scrolling down with a player on the bar still takes the top bar away`() {
        val t = barTravel(barsShowing = false, playerRestsOnBar = true)
        // The regression. Nothing rests on the top bar, so nothing keeps it.
        assertTrue(t.topHidden)
        // And the bottom one narrows rather than leaving, or the player goes
        // with it.
        assertFalse(t.bottomHidden)
        assertTrue(t.bottomCollapsed)
    }

    @Test
    fun `the bottom bar never leaves and collapses at once`() {
        for (showing in listOf(true, false)) {
            for (resting in listOf(true, false)) {
                val t = barTravel(showing, resting)
                assertFalse(
                    t.bottomHidden && t.bottomCollapsed,
                    "showing=$showing resting=$resting",
                )
            }
        }
    }
}
