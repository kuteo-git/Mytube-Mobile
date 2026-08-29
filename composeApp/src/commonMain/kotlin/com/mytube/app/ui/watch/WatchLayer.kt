package com.mytube.app.ui.watch

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.mytube.app.ui.theme.Tokens
import kotlinx.coroutines.launch

/**
 * The watch screen as a layer over the tab underneath, dismissed by dragging
 * down.
 *
 * The server charter describes exactly this and gives the reason: *"On phones,
 * the watch screen is a layer over the previous tab; pulling it down reveals the
 * tab underneath."* It is also the gesture named in this app's own brief, and it
 * is the one interaction on a phone that cannot be replaced by a button — a back
 * arrow does the same job in one tap, and people still reach for the drag,
 * because it is what every video app on the device does.
 *
 * ## Why the tab is composed underneath rather than restored afterwards
 *
 * A drag reveals what is behind it. If the tab is only rebuilt once the layer is
 * gone, the first half of the gesture uncovers an empty background and the feed
 * snaps in at the end — which reads as the app having reloaded rather than the
 * video having moved out of the way. Composing both means the feed is genuinely
 * there, at the scroll position it was left at, the whole way down.
 *
 * ## Why the drag is not consumed by the content
 *
 * `detectVerticalDragGestures` sits on this Box, above the screen it wraps, so
 * the gesture is read before anything below can scroll. That is right while the
 * watch screen does not scroll and is a decision to revisit the moment it does:
 * a page that scrolls needs the drag to start only at the top, or scrolling up
 * through comments closes the video.
 */
@Composable
fun WatchLayer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.toFloat() }
            .graphicsLayer { translationY = offset.value }
            .background(Tokens.bg)
            .pointerInput(height) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (shouldDismiss(offset.value, height)) {
                            // Carried the rest of the way rather than vanishing.
                            // A layer that disappears at the moment the finger
                            // lifts leaves the eye with nothing to follow, and
                            // the tab underneath appears to have jumped.
                            scope.launch {
                                offset.animateTo(height)
                                onDismiss()
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
        content()
    }
}
