package com.mytube.app.ui.cast

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A cover with black bars baked in: "cái cover image nó lúc vuông lúc chữ
 * nhật". Measured on the speaker's queue: five of six thumbnails were 1280×720
 * with the picture at 720×720 or about 960×720 between black pillars.
 */
class BlackBarsTest {

    /** A picture of luma 120 inside [box], black elsewhere. */
    private fun cover(box: Bounds): (Int, Int) -> Int = { x, y ->
        if (x in box.left until box.right && y in box.top until box.bottom) 120 else 4
    }

    @Test
    fun aSquarePictureBetweenPillarsIsFound() {
        val box = Bounds(280, 0, 1000, 720)
        assertEquals(box, contentBounds(1280, 720, cover(box)))
    }

    @Test
    fun aFourByThreePictureIsFound() {
        val box = Bounds(160, 0, 1120, 720)
        assertEquals(box, contentBounds(1280, 720, cover(box)))
    }

    @Test
    fun aWidePictureInAFourByThreeFrameLosesItsLetterbox() {
        // YouTube's hqdefault of a 16:9 video: 480×360 with bars above and below.
        val box = Bounds(0, 45, 480, 315)
        assertEquals(box, contentBounds(480, 360, cover(box)))
    }

    @Test
    fun aPictureThatFillsTheFrameIsLeftWhole() {
        assertEquals(Bounds(0, 0, 1280, 720), contentBounds(1280, 720) { _, _ -> 120 })
    }

    @Test
    fun aDarkSceneOnOneSideIsNotABar() {
        // Night on the left only: a pillarbox is the same on both sides.
        val lit = Bounds(400, 0, 1280, 720)
        assertEquals(Bounds(0, 0, 1280, 720), contentBounds(1280, 720, cover(lit)))
    }

    @Test
    fun aBrightLineInTheBarStillCountsAsPicture() {
        // JPEG leaves a little noise in a bar; a real picture column is mostly lit.
        val box = Bounds(280, 0, 1000, 720)
        val noisy: (Int, Int) -> Int = { x, y -> if (y == 100) 60 else cover(box)(x, y) }
        assertEquals(box, contentBounds(1280, 720, noisy))
    }
}
