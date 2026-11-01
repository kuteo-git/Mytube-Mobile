package com.mytube.app.ui.cast

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastDeviceKind
import com.mytube.app.domain.model.CastEnd
import com.mytube.app.domain.model.CastItem
import com.mytube.app.domain.model.CastSession
import com.mytube.app.domain.model.CastState
import com.mytube.app.ui.theme.MytubeTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The handle, driven by a finger: pressed, dragged to the bottom edge and held
 * there. The list must scroll under it and the row land beyond what the screen
 * first showed — "khi drag item lên top or bottom nó ko scroll".
 */
@OptIn(ExperimentalComposeUiApi::class)
class QueueDragGestureTest {

    private val session = CastSession(
        id = "s1", device = CastDevice("r1-living", "Loa R1", CastDeviceKind.R1),
        state = CastState.Playing, end = CastEnd.Running,
        queue = List(30) { CastItem("v$it", "Song $it", "Channel", "", 200) },
        currentIndex = 0, positionSeconds = 30, durationSeconds = 200, volume = 0, version = 1,
    )

    @Test
    fun aRowHeldAtTheBottomEdgeScrollsTheListAndLandsFurtherDown() {
        val list = LazyListState()
        val drag = QueueDrag()
        var moved: Pair<Int, Int>? = null
        val density = 3f
        val scene = ImageComposeScene(width = 390 * 3, height = 600 * 3, density = Density(density)) {
            MytubeTheme {
                LazyColumn(Modifier.fillMaxSize(), state = list) {
                    castQueueItems(session, "", drag, list, onJump = {}, onMove = { f, t -> moved = f to t }, onRemove = {})
                }
            }
        }
        var t = 0L
        fun frames(n: Int) = repeat(n) { scene.render(t); t += 16_000_000L }
        try {
            frames(5)
            val row = list.layoutInfo.visibleItemsInfo.first { it.key == "v2#0" }
            val lastVisible = list.layoutInfo.visibleItemsInfo.last().index
            // The handle sits at the row's start, inside the list's side margin.
            val x = (16 + 20) * density
            val y = row.offset + row.size / 2f
            val bottom = list.layoutInfo.viewportEndOffset - 10f

            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), timeMillis = t / 1_000_000)
            frames(2)
            var at = y
            while (at < bottom) {
                at = minOf(bottom, at + 40f)
                scene.sendPointerEvent(PointerEventType.Move, Offset(x, at), timeMillis = t / 1_000_000)
                frames(1)
            }
            frames(60) // held at the edge for a second
            assertTrue(list.firstVisibleItemIndex > 0, "the list did not scroll under the held row")
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, at), timeMillis = t / 1_000_000)
            frames(3)

            val (from, to) = assertNotNull(moved, "letting go moved nothing")
            assertEquals(2, from, "the row picked up is the one that moved")
            assertTrue(to > lastVisible, "landed at $to, no further than the screen first showed ($lastVisible)")
        } finally {
            scene.close()
        }
    }
}
