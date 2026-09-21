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
    fun `scrolling down with nothing playing takes the chips and leaves the tab bar`() {
        val t = barTravel(barsShowing = false, playerRestsOnBar = false)
        assertTrue(t.topHidden)
        // The tab bar is how somebody leaves the page, so it stays whole.
        assertFalse(t.bottomCollapsed)
    }

    @Test
    fun `scrolling down with a player on the bar still takes the top bar away`() {
        val t = barTravel(barsShowing = false, playerRestsOnBar = true)
        // The regression. Nothing rests on the top bar, so nothing keeps it.
        assertTrue(t.topHidden)
        // And the bottom one narrows to make room for the player.
        assertTrue(t.bottomCollapsed)
    }

    @Test
    fun `the tab bar only ever narrows, and only for a player`() {
        for (showing in listOf(true, false)) {
            for (resting in listOf(true, false)) {
                val t = barTravel(showing, resting)
                if (t.bottomCollapsed) {
                    assertTrue(resting, "collapsed with nothing to make room for")
                    assertFalse(showing, "collapsed while the reader is at the top")
                }
            }
        }
    }
}
