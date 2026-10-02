package com.mytube.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelOriginTest {

    @Test
    fun aChannelOpenedFromAListGoesBackToThatList() {
        assertEquals(Route.History, channelOrigin(current = Route.History, remembered = Route.Home))
    }

    @Test
    fun aChannelOpenedFromInsideAChannelKeepsTheFirstOrigin() {
        // Channel -> video -> the same channel's name. Back must leave the
        // channel, not land on it again.
        val origin = channelOrigin(current = Route.Channel("a"), remembered = Route.Search)
        assertEquals(Route.Search, origin)
    }

    @Test
    fun backFromAChannelNeverLeadsToAChannel() {
        var from: Route = Route.Home
        var route: Route = Route.Home
        for (id in listOf("a", "b", "a", "c")) {
            from = channelOrigin(route, from)
            route = Route.Channel(id)
        }
        assertEquals(Route.Home, from)
    }
}
