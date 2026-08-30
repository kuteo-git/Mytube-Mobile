package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.theme.Tokens

/**
 * A level control shaped like the one iOS draws.
 *
 * ## Why this is hand-drawn
 *
 * Material 3's `Slider` on an iPhone is unmistakable and was reported as such:
 * a thick stadium track, a tall bar for a thumb, and a visible gap between the
 * filled and unfilled halves. iOS draws a 4pt hairline with a 28pt white disc
 * riding it, and there is no gap.
 *
 * Calf would be the right place to get this from, and **it does not have one**:
 * `AdaptiveSlider` exists on its `main` branch and is not in the published
 * 0.8.0 — checked by unzipping the artifact, not by reading the docs, which
 * list it. Its `cupertino` package holds exactly one thing, a progress
 * indicator. So the choice is Material's shape on both platforms or thirty
 * lines here, and this is the control the two narration levels are set with.
 *
 * ## Why the same shape on Android
 *
 * A hairline with a round knob is not *wrong* on Android — it is what the
 * platform drew for years and what every media app still draws. One control
 * that reads correctly on both beats an `expect/actual` pair whose only job is
 * to make the two disagree.
 */
@Composable
fun LevelSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            // 44dp of target around a 4dp line. The line is what is drawn; the
            // height is what a thumb can hit, and 4dp is nothing to aim at.
            .height(TOUCH_HEIGHT),
    ) {
        val width = maxWidth
        var widthPx by remember { mutableStateOf(1f) }

        fun report(x: Float) {
            val f = (x / widthPx).coerceIn(0f, 1f)
            onValueChange(valueRange.start + f * span)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                // Measured from the same node the gesture reads, so a tap lands
                // where the knob is drawn rather than a knob's width away.
                .pointerInput(Unit) {
                    widthPx = size.width.toFloat().coerceAtLeast(1f)
                    detectTapGestures { report(it.x) }
                }
                .pointerInput(Unit) {
                    widthPx = size.width.toFloat().coerceAtLeast(1f)
                    detectHorizontalDragGestures { change, _ -> report(change.position.x) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // The track: one bar, filled to the value. Not two bars with a gap
            // between them, which is Material 3's shape and the thing that reads
            // as foreign here.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TRACK)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Tokens.surfaceHover),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Tokens.brand),
                )
            }

            // The knob, offset so it stays inside the track at both ends rather
            // than hanging half off the edge at 0 and at 1.
            Box(
                Modifier
                    .offset(x = (width - KNOB) * fraction)
                    .size(KNOB)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

private val TOUCH_HEIGHT = 44.dp
private val TRACK = 4.dp
private val KNOB = 28.dp
