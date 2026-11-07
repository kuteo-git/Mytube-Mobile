package com.mytube.app.ui

import com.mytube.app.ui.shell.Tab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Back returns to the page somebody was on, whichever page that was. */
class BackStackTest {

    private fun walk(vararg places: Place) = places.fold(BackStack()) { stack, place -> stack.push(place) }

    private fun BackStack.backs(): List<Place> = generateSequence(pop()) { it.second.pop() }.map { it.first }.toList()

    @Test
    fun backRetracesEveryStepInReverse() {
        val home = Place(Route.Home, Tab.Home, watched = "")
        val search = Place(Route.Search, Tab.Home, watched = "")
        val channel = Place(Route.Channel("a"), Tab.Home, watched = "")
        assertEquals(listOf(channel, search, home), walk(home, search, channel).backs())
    }

    @Test
    fun backReturnsToTheTabAPageWasOpenedFrom() {
        val playlists = Place(Route.Home, Tab.Playlists, watched = "")
        assertEquals(playlists, walk(playlists).pop()?.first)
    }

    /** Reported 2026-11-07: the watch screen, a channel's avatar, back, and Home. */
    @Test
    fun backReturnsToTheVideoAPageWasOpenedOver() {
        val overHome = Place(Route.Home, Tab.Home, watched = "v1")
        assertEquals("v1", walk(overHome).pop()?.first?.watched)
    }

    /** Channel, a video, the same channel's name: back walks out, never round. */
    @Test
    fun theSameChannelTwiceIsTwoStepsBackNotALoop() {
        val home = Place(Route.Home, Tab.Home, watched = "")
        val channelUnderVideo = Place(Route.Channel("a"), Tab.Home, watched = "v1")
        assertEquals(listOf(channelUnderVideo, home), walk(home, channelUnderVideo).backs())
    }

    @Test
    fun nothingLeftIsNoPlace() {
        assertNull(BackStack().pop())
    }
}
