package com.mytube.app.ui.tv

import com.mytube.app.domain.model.Profile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the launch stops to ask who is watching.
 *
 * "On a phone this is never shown" is the half nobody would think to check by
 * hand, so it is the first thing checked here.
 */
class WhoIsWatchingTest {

    private val household = listOf(Profile("a", "Lan"), Profile("b", "Minh"), Profile("c", "Bà"))

    @Test
    fun `a phone never asks, however large the household`() {
        assertFalse(asksWhoIsWatching(television = false, profiles = household, chosenId = ""))
        assertFalse(asksWhoIsWatching(television = false, profiles = household.take(1), chosenId = ""))
        assertFalse(asksWhoIsWatching(television = false, profiles = emptyList(), chosenId = ""))
    }

    /** There is nobody to choose between. */
    @Test
    fun `a television does not ask a household of one`() {
        assertFalse(asksWhoIsWatching(television = true, profiles = household.take(1), chosenId = ""))
    }

    /** A fresh install, before the server has answered with anybody. */
    @Test
    fun `a television does not ask when there are no profiles`() {
        assertFalse(asksWhoIsWatching(television = true, profiles = emptyList(), chosenId = ""))
    }

    @Test
    fun `a television nobody has answered for asks once there are two to choose between`() {
        assertTrue(asksWhoIsWatching(television = true, profiles = household.take(2), chosenId = ""))
        assertTrue(asksWhoIsWatching(television = true, profiles = household, chosenId = ""))
    }

    /**
     * Reported from the sofa: the question came back on every launch. Once
     * answered it is remembered, and changed from Settings → Profile.
     */
    @Test
    fun `a television that has been answered for does not ask again`() {
        assertFalse(asksWhoIsWatching(television = true, profiles = household, chosenId = "b"))
    }

    /** The member chosen was removed on the server: the answer no longer names anybody. */
    @Test
    fun `a television asks again when the one chosen has left the household`() {
        assertTrue(asksWhoIsWatching(television = true, profiles = household, chosenId = "z"))
    }
}
