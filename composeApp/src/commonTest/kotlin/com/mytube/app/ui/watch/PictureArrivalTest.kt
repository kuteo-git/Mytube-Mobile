package com.mytube.app.ui.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The controls start hidden whenever the picture arrives: a video opened, the
 * miniplayer expanded, fullscreen entered or left. Asked for on 2026-11-07,
 * "tao quan tâm video hơn": the buttons and the bar coming up over the picture
 * at each of those moments were in the way of the thing just asked for.
 */
class PictureArrivalTest {

    @Test
    fun anotherVideoIsAnArrival() {
        assertNotEquals(pictureArrival("a", false, 0f), pictureArrival("b", false, 0f))
    }

    @Test
    fun enteringFullscreenIsAnArrival() {
        assertNotEquals(pictureArrival("a", false, 0f), pictureArrival("a", true, 0f))
    }

    @Test
    fun expandingFromTheMiniplayerIsAnArrival() {
        assertNotEquals(pictureArrival("a", false, 1f), pictureArrival("a", false, 0f))
    }

    // Otherwise every frame of the drag would hide them again.
    @Test
    fun theDragItselfIsNot() {
        assertEquals(pictureArrival("a", false, 0.2f), pictureArrival("a", false, 0.9f))
    }
}
