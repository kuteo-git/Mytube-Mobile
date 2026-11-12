package com.mytube.app.ui.watch

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * The page under the picture pulled down, carrying the picture to the middle of
 * the screen, where letting go enters [Fullscreen.Portrait].
 *
 * Read from the page's own scrolling rather than from a second drag detector:
 * only the scroll left over once the list is at its top arrives here, so a
 * finger reading the comments scrolls them and the same finger at the top
 * pulls. The picture's own drag, to the miniplayer, never passes through it.
 *
 * [offset] is how far the page has come, in pixels, out of [distance], which is
 * the way from where the picture sits to the middle of the screen.
 */
@Stable
internal class FullscreenPull(
    /** Whether a pull can start now: a playing video, not a speaker's song. */
    private val enabled: () -> Boolean,
    /** A fling faster than this, downward, commits however short the pull, in px/s. */
    private val flingVelocity: Float,
    /** Called once per release, with whether the pull went far enough. */
    private val onRelease: FullscreenPull.(commit: Boolean) -> Unit,
) {
    var offset by mutableFloatStateOf(0f)
        private set

    var distance by mutableFloatStateOf(1f)

    val progress: Float get() = pullProgress(offset, distance)

    val connection = object : NestedScrollConnection {
        // An upward move gives back the pull before the list scrolls, so
        // changing one's mind halfway closes the page instead of scrolling
        // the comments under a picture still hanging in the middle.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (offset <= 0f || available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            val before = offset
            offset = (offset + available.y).coerceAtLeast(0f)
            return Offset(0f, offset - before)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            if (offset <= 0f && !enabled()) return Offset.Zero
            offset = (offset + available.y).coerceAtMost(distance)
            // All of it, at the limit too: what is left would be the list's
            // overscroll stretching under a picture that has stopped.
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset <= 0f) return Velocity.Zero
            onRelease(pullCommits(progress, available.y, flingVelocity))
            return available
        }
    }

    /** Carries the page the rest of the way, or back, from wherever it is. */
    suspend fun settle(commit: Boolean) {
        animate(offset, if (commit) distance else 0f, animationSpec = spring(stiffness = PULL_STIFFNESS)) { value, _ ->
            offset = value
        }
    }

    /** The picture in the middle with the page under it, where leaving [Fullscreen.Portrait] starts. */
    fun atCentre() {
        offset = distance
    }

    fun reset() {
        offset = 0f
    }

    /** Follows a finger dragging the page back up from the middle, within its travel. */
    fun dragBy(dy: Float) {
        offset = (offset + dy).coerceIn(0f, distance)
    }
}

/** How far through the pull [offset] is, 0 to 1. */
internal fun pullProgress(offset: Float, distance: Float): Float =
    if (distance <= 0f) 0f else (offset / distance).coerceIn(0f, 1f)

/**
 * Whether a released pull enters fullscreen: past [PULL_COMMIT] of the way, or
 * flung downward faster than [flingVelocity] from any distance. A fling upward
 * never does, wherever it is let go.
 */
internal fun pullCommits(progress: Float, velocity: Float, flingVelocity: Float): Boolean = when {
    progress <= 0f -> false
    velocity < -flingVelocity -> false
    velocity > flingVelocity -> true
    else -> progress >= PULL_COMMIT
}

/**
 * Whether upright fullscreen dragged up from the middle and let go returns to
 * the page: the pull's own rule, read the other way. [progress] is still the
 * page's, so 1 is not moved and 0 is home.
 */
internal fun returnCommits(progress: Float, velocity: Float, flingVelocity: Float): Boolean =
    pullCommits(1f - progress, -velocity, flingVelocity)

/**
 * How far down the picture travels: from its place under the status bar to
 * the middle of the screen, which is where [Fullscreen.Portrait] draws it.
 */
internal fun pullDistance(screenHeight: Float, pictureHeight: Float, pictureTop: Float): Float =
    ((screenHeight - pictureHeight) / 2f - pictureTop).coerceAtLeast(1f)

/**
 * A vertical drag in upright fullscreen, while [active] says it is one.
 *
 * Up is the pull played backwards and follows the finger: [onReturnStart] lays
 * the page out again with the picture in the middle, [onReturnDrag] moves it,
 * [onReturnEnd] is handed the release velocity. Down, [SWIPE_TO_LEAVE] or more,
 * is [onLeave]. Every vertical move is consumed, so the drag to the miniplayer
 * around the page never starts from fullscreen.
 *
 * On the screen's root rather than on the picture: the first move up replaces
 * the fullscreen layout, and a gesture read by a node of that layout would be
 * cancelled with it. Keyed on nothing for the same reason; the callbacks are
 * read through state by the caller.
 */
internal fun Modifier.fullscreenDrag(
    active: () -> Boolean,
    onLeave: () -> Unit,
    onReturnStart: () -> Unit,
    onReturnDrag: (Float) -> Unit,
    onReturnEnd: (velocity: Float) -> Unit,
): Modifier = pointerInput(Unit) {
    // focus-guard: upright fullscreen exists only on a phone; the remote leaves the TV's player with Back.
    val threshold = SWIPE_TO_LEAVE.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!active()) return@awaitEachGesture
        var first = 0f
        val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            first = over
        } ?: return@awaitEachGesture
        if (first < 0f) {
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(start)
            onReturnStart()
            onReturnDrag(first)
            val lifted = verticalDrag(start.id) { change ->
                tracker.addPointerInputChange(change)
                onReturnDrag(change.positionChange().y)
                change.consume()
            }
            onReturnEnd(if (lifted) tracker.calculateVelocity().y else 0f)
        } else {
            var travelled = first
            verticalDrag(start.id) { change ->
                travelled += change.positionChange().y
                change.consume()
            }
            if (travelled >= threshold) onLeave()
        }
    }
}

internal const val PULL_COMMIT = 0.35f
internal val PULL_FLING = 1000.dp
private val SWIPE_TO_LEAVE = 64.dp
private const val PULL_STIFFNESS = 500f
