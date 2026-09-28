package com.mytube.app.ui

import com.mytube.app.ui.watch.scrubPreviewScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scrub preview stands in for the picture, so it has to be the same shape as
 * the picture.
 *
 * Reported from a phone: in fullscreen, pinch out until the video reaches both
 * edges, then drag the bar — and the still appears in a letterbox inside a
 * screen the video was filling. Measured on the emulator at the time: the video
 * had black bars of 0px and the preview 240px.
 */
class ScrubPreviewScaleTest {

    // A 2400x1080 screen — 20:9, which is what a phone turned sideways is — and
    // the 16:9 stills the server copies for a landscape video.
    private val screenWidth = 2400f
    private val screenHeight = 1080f
    private val tileWidth = 320
    private val tileHeight = 180

    @Test
    fun fittedLeavesTheSameLetterboxTheVideoDoes() {
        val scale = scrubPreviewScale(screenWidth, screenHeight, tileWidth, tileHeight, fill = false)
        // Height is the binding side on a screen wider than 16:9.
        assertEquals(6f, scale)
        val drawnWidth = tileWidth * scale
        assertEquals(1920f, drawnWidth)
        assertEquals(240f, (screenWidth - drawnWidth) / 2f)
    }

    @Test
    fun filledReachesBothEdges() {
        val scale = scrubPreviewScale(screenWidth, screenHeight, tileWidth, tileHeight, fill = true)
        assertEquals(7.5f, scale)
        // No letterbox at all: the still is cropped top and bottom instead, which
        // is exactly what the video does under the same gesture.
        assertTrue(tileWidth * scale >= screenWidth)
        assertTrue(tileHeight * scale >= screenHeight)
    }

    @Test
    fun aPortraitStillIsStillFittedWhenTheVideoIs() {
        // A portrait upload arrives as a narrow tile of the same height, and
        // letterboxing it is correct — it is what the picture does while it
        // plays. The bug was never about portrait; it was about which rule.
        val scale = scrubPreviewScale(screenWidth, screenHeight, 101, 180, fill = false)
        assertEquals(6f, scale)
        assertTrue(101 * scale < screenWidth)
    }

    @Test
    fun aBoardWithNoSizeDrawsNothingRatherThanDividingByZero() {
        assertEquals(0f, scrubPreviewScale(screenWidth, screenHeight, 0, 180, fill = false))
        assertEquals(0f, scrubPreviewScale(screenWidth, screenHeight, 320, 0, fill = true))
    }
}
