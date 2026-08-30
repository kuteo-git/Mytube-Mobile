package com.mytube.app.ui.watch

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.mytube.app.ui.theme.Tokens
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * How far the video has been dragged toward the miniplayer, 0 to 1.
 *
 * Read by the watch screen so the picture can shrink toward the bar while
 * everything under it fades. A composition local rather than a parameter: the
 * value is an ambient fact about the layer, every part of the screen inside it
 * reacts to the same number, and threading it through would mean the one part
 * that forgot to pass it on stays solid while the rest fades.
 */
val LocalDragProgress = compositionLocalOf { 0f }

/**
 * How far the picture has to travel, in pixels, to reach the bar.
 *
 * Measured by the layer and read by the screen, because only the layer knows the
 * height and only the screen draws the picture. Without it the picture shrank
 * where it stood and the whole layer faded out — so the video did not move to
 * the miniplayer, it *vanished*, and the bar appeared somewhere else a moment
 * later with nothing connecting the two.
 */
val LocalDragTravel = compositionLocalOf { 0f }

/**
 * The watch screen as a layer over the tab underneath, dragged down into the
 * miniplayer.
 *
 * The server charter describes exactly this: *"On phones, the watch screen is a
 * layer over the previous tab; pulling it down reveals the tab underneath."*
 * What the drag ends in is the miniplayer, not a closed video — see [MiniPlayer]
 * for why.
 *
 * ## Why the tab is composed underneath rather than restored afterwards
 *
 * A drag reveals what is behind it. If the tab is only rebuilt once the layer is
 * gone, the first half of the gesture uncovers an empty background and the feed
 * snaps in at the end — which reads as the app having reloaded rather than the
 * video having moved out of the way.
 *
 * ## Why the gesture is not consumed by the content
 *
 * `detectVerticalDragGestures` sits on this Box, above the screen it wraps, so
 * the drag is read before anything below can scroll. That is right while the
 * watch screen does not scroll and is a decision to revisit the moment it does:
 * a page that scrolls needs the drag to start only at the top, or scrolling up
 * through the comments closes the video.
 */
@Composable
fun WatchLayer(
    onMinimise: () -> Unit,
    /**
     * Where the picture has to arrive, as a distance up from the bottom of this
     * layer, in pixels.
     *
     * This is the top edge of the miniplayer bar. It is passed in rather than
     * measured here because only the app knows where that bar will be: it
     * depends on the navigation inset, on whether the screen underneath has a
     * tab bar at all, and — since the bars now leave on scroll — on how far the
     * tab bar has already slid away.
     *
     * Without it `travel` ran to the bottom of the *screen*, so the picture was
     * dragged well past the bar and then jumped back up to it on release. With
     * the tab bar hidden the gap was larger still, which is exactly how it was
     * reported.
     */
    landingFromBottomPx: Float,
    /** The status bar's height: where the picture's own box begins. */
    topInsetPx: Float,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf(0f) }
    // The picture's own height, which is what the commit threshold is a share
    // of. 16:9 of the width, the same shape the watch screen draws.
    var pictureHeight by remember { mutableStateOf(0f) }
    var startedAt by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }

    // The distance the picture has to cover: from where it sits, just under the
    // status bar, down to the top of the miniplayer bar. Measured and passed in
    // rather than chosen — see `travelProgress` for why using the picture's own
    // height instead makes it arrive while the finger is still half a screen
    // away, and `landingFromBottomPx` for why the bottom of the screen is the
    // wrong target.
    val travel = (height - topInsetPx - landingFromBottomPx).coerceAtLeast(1f)
    val progress = travelProgress(offset.value, travel)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                height = it.height.toFloat()
                pictureHeight = it.width * 9f / 16f
            }
            // The *ground* fades, not the layer.
            //
            // `graphicsLayer { alpha }` here was the fault behind "the player
            // disappears instead of shrinking": alpha on this Box applies to
            // everything inside it, picture included, so by the time the video
            // had travelled far enough to be recognisable as heading for the bar
            // it was already nine-tenths transparent. Fading the background
            // colour instead lets the tab underneath come through while the
            // picture stays solid all the way down.
            .background(Tokens.bg.copy(alpha = 1f - progress))
            .pointerInput(height, pictureHeight) {
                detectVerticalDragGestures(
                    onDragStart = { startedAt = TimeSource.Monotonic.markNow() },
                    onDragEnd = {
                        val elapsed = startedAt.elapsedNow().inWholeMilliseconds
                        val committed = shouldCommit(
                            dy = offset.value,
                            playerHeight = pictureHeight,
                            velocity = velocityOf(offset.value, elapsed),
                        )
                        if (committed) {
                            // Carried the rest of the way rather than vanishing.
                            // A layer that disappears when the finger lifts
                            // leaves the eye with nothing to follow, and the tab
                            // underneath appears to have jumped.
                            scope.launch {
                                offset.animateTo(travel)
                                onMinimise()
                            }
                        } else {
                            scope.launch { offset.animateTo(0f) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    onVerticalDrag = { _, delta ->
                        // snapTo, not animateTo: this is following a finger, and
                        // anything with a duration lags it visibly.
                        scope.launch { offset.snapTo(dragOffset(offset.value + delta)) }
                    },
                )
            },
    ) {
        CompositionLocalProvider(
            LocalDragProgress provides progress,
            LocalDragTravel provides travel,
        ) {
            content()
        }
    }
}
