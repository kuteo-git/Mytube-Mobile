package com.mytube.app.ui.watch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShowsReflectionTest {

    private fun shows(
        effects: Boolean = true,
        playing: Boolean = true,
        casting: Boolean = false,
        fullscreen: Boolean = false,
        drag: Float = 0f,
    ) = showsReflection(effects, playing, casting, fullscreen, drag)

    @Test
    fun aVideoPlayingOnThePortraitPageIsReflected() {
        assertTrue(shows())
    }

    @Test
    fun noReflectionWithEffectsOff() {
        assertFalse(shows(effects = false))
    }

    @Test
    fun noReflectionWithoutAPlayingVideo() {
        assertFalse(shows(playing = false))
    }

    @Test
    fun noReflectionWhileASpeakerPlays() {
        assertFalse(shows(casting = true))
    }

    @Test
    fun noReflectionInFullscreen() {
        assertFalse(shows(fullscreen = true))
    }

    /** iOS cannot fade an interop view, so it goes on the first pixel of the drag or it covers the miniplayer. */
    @Test
    fun noReflectionFromTheFirstPixelOfTheDrag() {
        assertFalse(shows(drag = 0.001f))
        assertFalse(shows(drag = 1f))
    }
}
