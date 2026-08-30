package com.mytube.app.ui.shell

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
 * Swipe from the left edge to go back.
 *
 * ## Why this exists
 *
 * Android has a system back gesture and iOS has none — an app there is expected
 * to provide its own, which is why every iOS app answers a drag from the left
 * edge. Without it the channel page, search and the saved shelf were reachable
 * by a tap and leavable only by aiming at a 48dp arrow in the corner, on a phone
 * held in one hand.
 *
 * ## Why it is not the platform's predictive back
 *
 * Compose Multiplatform has no shared predictive-back API, and wiring Android's
 * to this while iOS gets something hand-rolled would be two gestures that feel
 * different on the two devices in the same house. The route transition already
 * draws the movement; this decides when it starts.
 *
 * ## What it deliberately does not do
 *
 * It does not drag the screen along under the finger. That needs the *outgoing*
 * page to be composed at the same time, which the route transition arranges only
 * once the gesture has committed. What is here is the trigger; making it follow
 * the finger is a change to `AnimatedContent`, not to this file, and is worth
 * doing separately rather than half-doing here.
 */
fun Modifier.edgeBack(onBack: () -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val edge = with(density) { EDGE_WIDTH.toPx() }

    pointerInput(onBack) {
        val commit = size.width * COMMIT_FRACTION
        var travelled = 0f
        var fromEdge = false

        detectHorizontalDragGestures(
            // Where the finger *landed* decides whether this is a back gesture
            // at all. Judging it later, from the direction of travel, would
            // claim every rightward drag anywhere on the page.
            onDragStart = { start ->
                fromEdge = start.x <= edge
                travelled = 0f
            },
            onDragEnd = {
                if (fromEdge && travelled >= commit) onBack()
                fromEdge = false
                travelled = 0f
            },
            onDragCancel = {
                fromEdge = false
                travelled = 0f
            },
            onHorizontalDrag = { change, delta ->
                if (!fromEdge) return@detectHorizontalDragGestures
                travelled += delta
                // Consumed only for a gesture that began at the edge, so a
                // horizontal scroll anywhere else keeps working.
                change.consume()
            },
        )
    }
}
