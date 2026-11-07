package com.mytube.app.ui.shell

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * How wide the strip is that starts a back gesture.
 *
 * 20dp, which is what iOS itself uses for the interactive pop gesture. Wider
 * would be easier to hit and would start eating drags meant for the content —
 * on this app the channel page's sort row scrolls horizontally, and a 48dp strip
 * would swallow the first flick of it every time.
 */
private val EDGE_WIDTH = 20.dp

/**
 * How far the finger has to travel before the screen goes back.
 *
 * A quarter of the width, the same fraction the drag-to-miniplayer gesture
 * commits at. Two gestures in one app should not disagree about what counts as
 * a deliberate movement.
 */
private const val COMMIT_FRACTION = 0.25f

/**
 * Swipe from the left edge to go back, with the page following the finger.
 *
 * ## Why this exists
 *
 * Android has a system back gesture and iOS has none — an app there is expected
 * to provide its own, which is why every iOS app answers a drag from the left
 * edge. Without it the channel page, search and the saved shelf were reachable
 * by a tap and leavable only by aiming at a 48dp arrow in the corner, on a phone
 * held in one hand.
 *
 * ## How the page follows
 *
 * [onDrag] reports how far across the screen the finger is, 0..1, and the
 * caller seeks the route transition to it, so the page under is drawn sliding
 * in as the one on top leaves, as iOS does. [onRelease] says whether the drag
 * went far enough to go back; when it did not, the caller springs the page back.
 */
fun Modifier.edgeBack(onDrag: (Float) -> Unit, onRelease: (commit: Boolean) -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val edge = with(density) { EDGE_WIDTH.toPx() }
    // Read through state, so a recomposition mid-drag does not restart the
    // detector and drop the gesture under the finger.
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)

    pointerInput(Unit) {
        var travelled = 0f
        var fromEdge = false

        // focus-guard: a swipe from the screen's edge is the phone's back. A remote has a Back key, and `App` does not attach this on a television.
        detectHorizontalDragGestures(
            // Where the finger *landed* decides whether this is a back gesture
            // at all. Judging it later, from the direction of travel, would
            // claim every rightward drag anywhere on the page.
            onDragStart = { start ->
                fromEdge = start.x <= edge
                travelled = 0f
            },
            onDragEnd = {
                if (fromEdge) release(travelled >= size.width * COMMIT_FRACTION)
                fromEdge = false
            },
            onDragCancel = {
                if (fromEdge) release(false)
                fromEdge = false
            },
            onHorizontalDrag = { change, delta ->
                if (!fromEdge) return@detectHorizontalDragGestures
                travelled = (travelled + delta).coerceAtLeast(0f)
                drag((travelled / size.width).coerceIn(0f, 1f))
                // Consumed only for a gesture that began at the edge, so a
                // horizontal scroll anywhere else keeps working.
                change.consume()
            },
        )
    }
}
