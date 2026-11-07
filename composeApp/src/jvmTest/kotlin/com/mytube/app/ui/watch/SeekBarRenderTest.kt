package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.mytube.app.ui.theme.Tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The seek bar drawn and touched at density 1, so a pixel is a dp: the line
 * at rest is 2dp, and the 32dp target above it still takes a dragging thumb.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SeekBarRenderTest {

    private val width = 200
    private val height = 100

    private fun scene(onSeekFraction: (Double) -> Unit) =
        ImageComposeScene(width = width, height = height, density = Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.BottomCenter) {
                SeekBar(
                    progress = 0.5f,
                    enabled = true,
                    expanded = false,
                    scrub = -1f,
                    onScrub = {},
                    onSeekFraction = onSeekFraction,
                    onInteract = {},
                )
            }
        }

    @Test
    fun theLineAtRestIsTwoDpTall() {
        val scene = scene {}
        try {
            val pixels = scene.render().toComposeImageBitmap().toPixelMap()
            val played = (0 until height).count { pixels[width / 4, it] == Tokens.brand }
            assertEquals(2, played, "rows of the played part of the line")
            val rest = (0 until height).count { pixels[width * 3 / 4, it] != Color.Black }
            assertEquals(2, rest, "rows of the unplayed part of the line")
        } finally {
            scene.close()
        }
    }

    /** Reported 2026-11-07: a tap meant for the picture landed on the bar and moved the video. */
    @Test
    fun aTapOnTheBarSeeksNothing() {
        var sought: Double? = null
        val scene = scene { sought = it }
        try {
            var t = 0L
            fun frames(n: Int) = repeat(n) { scene.render(t); t += 16_000_000L }
            frames(3)
            val at = Offset(width * 0.75f, height - 30f)
            scene.sendPointerEvent(PointerEventType.Press, at, timeMillis = t / 1_000_000)
            frames(2)
            scene.sendPointerEvent(PointerEventType.Release, at, timeMillis = t / 1_000_000)
            frames(3)
            assertNull(sought, "a tap on the bar moved the video")
        } finally {
            scene.close()
        }
    }

    @Test
    fun aDragThirtyDpAboveTheLineSeeksWhereItWasLetGo() {
        var sought: Double? = null
        val scene = scene { sought = it }
        try {
            var t = 0L
            fun frames(n: Int) = repeat(n) { scene.render(t); t += 16_000_000L }
            frames(3)
            val y = height - 30f
            scene.sendPointerEvent(PointerEventType.Press, Offset(width * 0.25f, y), timeMillis = t / 1_000_000)
            for (x in listOf(0.35f, 0.5f, 0.65f, 0.75f)) {
                frames(1)
                scene.sendPointerEvent(PointerEventType.Move, Offset(width * x, y), timeMillis = t / 1_000_000)
            }
            frames(1)
            scene.sendPointerEvent(PointerEventType.Release, Offset(width * 0.75f, y), timeMillis = t / 1_000_000)
            frames(3)
            assertEquals(0.75, assertNotNull(sought, "a drag along the bar sought nothing"), 0.01)
        } finally {
            scene.close()
        }
    }
}
