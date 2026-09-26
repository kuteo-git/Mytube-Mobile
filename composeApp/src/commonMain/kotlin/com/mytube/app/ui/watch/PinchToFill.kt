package com.mytube.app.ui.watch

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The ratio the fingers have to open by, past the last decision, to crop the
 * picture to the screen — and the ratio they have to close by to give it back.
 *
 * They are not symmetric around 1 by accident: the accumulator below starts at
 * whichever of them describes the state the picture is already in, so flipping
 * always costs the same movement in either direction (`1.12 / 0.89`, about a
 * quarter). Reading them as "12% out" or "11% in" is the wrong reading.
 */
private const val PINCH_FILL = 1.12f
private const val PINCH_FIT = 0.89f

/**
 * Whether the picture should fill the screen, given how far a pinch has run.
 *
 * [scale] is the distance between the fingers now, as a ratio of what it was
 * when the picture last changed its mind — clamped by the caller to the band
 * between the two thresholds, so how far *past* one of them somebody pinched
 * does not matter. Everything between them leaves [filled] alone, which is the
 * hysteresis: a pinch that wobbles across a threshold must not flicker the
 * picture, and a viewer who has committed should not have it undone by the last
 * few pixels of their own gesture.
 *
 * A named function with a test rather than an expression inside the gesture
 * loop, for the reason `wholeSeconds`, `barTravel` and `shouldRecoverStall` are
 * ones: nothing in the type system catches a threshold that is the wrong way
 * round. Both directions compile, and being wrong shows up as a gesture that
 * feels sticky rather than as anything that fails.
 */
fun fillFromPinch(scale: Float, filled: Boolean): Boolean = when {
    scale >= PINCH_FILL -> true
    scale <= PINCH_FIT -> false
    else -> filled
}

/**
 * Pinch the picture to crop it to the screen, and pinch back to letterbox it.
 *
 * ## Why this is written out rather than `detectTransformGestures`
 *
 * That detector reports a pan for a **single** finger too, and it consumes the
 * events it reads. The watch screen is a layer whose one-finger vertical drag
 * collapses the video into the miniplayer — so dropping the ready-made detector
 * here would take that gesture away, and the two are hard to tell apart from the
 * outside: the video would simply stop being draggable in fullscreen.
 *
 * So this waits for a **second** pointer before it consumes anything. Up to that
 * moment every event passes through untouched, which is what leaves the drag,
 * the tap that shows the controls and the double tap that seeks exactly as they
 * were. Once two fingers are down it consumes every change, and *that* is what
 * stops the collapse drag from running underneath a pinch — the two fingers'
 * centroid moves as they open, and without the consume the video would be
 * travelling toward the bar while it was being zoomed.
 *
 * `awaitFirstDown(requireUnconsumed = false)`, because the controls' own
 * `detectTapGestures` is drawn over the picture and has already consumed the
 * first press by the time this sees it.
 *
 * ## Why the accumulator starts where the picture already is
 *
 * `calculateZoom` reports the change since the *previous* event — a number a
 * hair either side of 1 — so the gesture has to keep its own total. Starting
 * that total at 1 would mean a fresh pinch-in needs 11% to undo a fill that took
 * 12% to ask for, while the same reversal *within* one gesture needs the whole
 * 26%. Starting it at the threshold the current state sits on makes the two the
 * same movement, and makes the clamp the only thing holding the memory.
 */
fun Modifier.pinchToFill(
    /** Only in fullscreen: elsewhere the picture's box is 16:9 already. */
    enabled: Boolean,
    filled: Boolean,
    onFilled: (Boolean) -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(filled) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var scale = if (filled) PINCH_FILL else PINCH_FIT
        var decided = filled
        var pinching = false
        while (true) {
            val event = awaitPointerEvent()
            val down = event.changes.count { it.pressed }
            if (down < 2) {
                // One finger is either the start of a pinch that has not
                // arrived yet, or the collapse drag. Either way it is not this
                // gesture's to read — and once a pinch has ended, a finger left
                // on the glass must not go on zooming.
                if (pinching || down == 0) break
                continue
            }
            pinching = true
            scale = (scale * event.calculateZoom()).coerceIn(PINCH_FIT, PINCH_FILL)
            val next = fillFromPinch(scale, decided)
            if (next != decided) {
                decided = next
                onFilled(next)
            }
            event.changes.forEach { it.consume() }
        }
    }
}
