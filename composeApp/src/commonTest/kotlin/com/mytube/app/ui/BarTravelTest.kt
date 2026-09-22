package com.mytube.app.ui

import com.mytube.app.ui.shell.TabDragOutcome
import com.mytube.app.ui.shell.TabPress
import com.mytube.app.ui.shell.barTravel
import com.mytube.app.ui.shell.dragOutcome
import com.mytube.app.ui.shell.tabAt
import com.mytube.app.ui.shell.tabPress
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

    @Test
    fun `a press on a collapsed bar only opens it`() {
        // The reported bug: it opened *and* threw the reader back to the top of
        // a feed they had scrolled a long way down.
        assertEquals(
            TabPress.Reveal,
            tabPress(barCollapsed = true, pickedIsCurrent = true),
        )
    }

    @Test
    fun `a collapsed bar swallows the press whichever tab it was`() {
        assertEquals(
            TabPress.Reveal,
            tabPress(barCollapsed = true, pickedIsCurrent = false),
        )
    }

    @Test
    fun `the tab you are on scrolls to the top while the bar is whole`() {
        assertEquals(
            TabPress.ScrollToTop,
            tabPress(barCollapsed = false, pickedIsCurrent = true),
        )
    }

    @Test
    fun `another tab is a switch and moves no list`() {
        assertEquals(
            TabPress.Switch,
            tabPress(barCollapsed = false, pickedIsCurrent = false),
        )
    }

    @Test
    fun `a finger divides the capsule into equal tabs`() {
        // 300px across three tabs: 0..99, 100..199, 200..299.
        assertEquals(0, tabAt(0f, 300f, 3))
        assertEquals(0, tabAt(99f, 300f, 3))
        assertEquals(1, tabAt(100f, 300f, 3))
        assertEquals(1, tabAt(199f, 300f, 3))
        assertEquals(2, tabAt(200f, 300f, 3))
        assertEquals(2, tabAt(299f, 300f, 3))
    }

    @Test
    fun `sliding off the capsule keeps the outermost tab`() {
        // Asked for by name: releasing outside the capsule still commits to the
        // tab the pill is on.
        assertEquals(0, tabAt(-500f, 300f, 3))
        assertEquals(2, tabAt(9000f, 300f, 3))
    }

    @Test
    fun `an unmeasured capsule cannot be divided`() {
        assertEquals(0, tabAt(50f, 0f, 3))
    }

    @Test
    fun `landing on another tab goes there`() {
        assertEquals(
            TabDragOutcome.Switch,
            dragOutcome(from = 0, landed = 2, leftItsTab = true),
        )
    }

    @Test
    fun `a wobble that never left its tab is still a press`() {
        assertEquals(
            TabDragOutcome.Press,
            dragOutcome(from = 1, landed = 1, leftItsTab = false),
        )
    }

    @Test
    fun `going away and coming back is a cancel, not a press`() {
        // The distinction the whole gesture turns on: both of these end where
        // they started.
        assertEquals(
            TabDragOutcome.Cancel,
            dragOutcome(from = 1, landed = 1, leftItsTab = true),
        )
    }
}
