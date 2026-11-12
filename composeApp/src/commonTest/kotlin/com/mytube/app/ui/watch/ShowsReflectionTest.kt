package com.mytube.app.ui.watch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShowsReflectionTest {

    private fun shows(
        effects: Boolean = true,
        playing: Boolean = true,
        casting: Boolean = false,
        fullscreen: Fullscreen = Fullscreen.Off,
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
    fun noReflectionSideways() {
        assertFalse(shows(fullscreen = Fullscreen.Landscape))
    }

    /** The pull carries the band down under the picture, and upright fullscreen keeps it. */
    @Test
    fun uprightFullscreenIsReflected() {
        assertTrue(shows(fullscreen = Fullscreen.Portrait))
    }

    /** iOS cannot fade an interop view, so it goes on the first pixel of the drag or it covers the miniplayer. */
    @Test
    fun noReflectionFromTheFirstPixelOfTheDrag() {
        assertFalse(shows(drag = 0.001f))
        assertFalse(shows(drag = 1f))
    }
}
