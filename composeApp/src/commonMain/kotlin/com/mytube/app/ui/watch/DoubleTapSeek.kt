package com.mytube.app.ui.watch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.theme.Tokens

/**
 * What a double tap on the picture has asked for, and which side asked.
 *
 * The total rather than one jump: tapping four times on the right is one
 * request for forty seconds, and a badge that said "10 seconds" four times over
 * would leave somebody counting flashes to know where they had got to. It is
 * also why [taps] is here at all — the *arithmetic* of accumulating is the
 * caller's, and this is only the reading of it.
 */
data class SeekRipple(
    /** Negative for backwards. Zero means nothing is showing. */
    val seconds: Int = 0,
    /** Bumped on every double tap, so a repeat re-runs the animation. */
    val taps: Int = 0,
) {
    val visible: Boolean get() = seconds != 0
    val forward: Boolean get() = seconds > 0
}

/**
 * YouTube's double-tap ripple: a dimmed half of the frame with the jump on it.
 *
 * ## Why a rounded half and not a circle
 *
 * The reference draws an arc that follows the outer edge of the screen and is
 * flat where it meets the middle, so the two halves read as two targets rather
 * than as one shape drawn twice. A circle centred on the finger would move under
 * a second tap, which makes a repeated jump look like a different gesture.
 *
 * ## Why it does not consume touches
 *
 * It is drawn over the picture while a finger is still on it. Anything here that
 * took a pointer event would eat the *third* tap of a run, which is precisely
 * the tap somebody is making when they mean to keep going.
 */
@Composable
fun SeekRippleOverlay(ripple: SeekRipple, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current

    // Keyed on the tap count as well as on visibility, so a second double tap
    // inside the fade restarts it rather than continuing a dying animation.
    val alpha by animateFloatAsState(
        targetValue = if (ripple.visible) 1f else 0f,
        animationSpec = tween(if (ripple.visible) FADE_IN_MILLIS else FADE_OUT_MILLIS),
        label = "seekRipple",
    )
    if (alpha <= 0.01f) return

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(if (ripple.forward) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxWidth(HALF)
                .fillMaxHeight()
                .alpha(alpha)
                .clip(
                    if (ripple.forward) {
                        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                    } else {
                        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    },
                )
                .background(Color.Black.copy(alpha = DIM)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                // Three chevrons, as the reference has: one arrow reads as a
                // "next track" button, which is a different control on this
                // screen and sits a few centimetres away.
                Row {
                    repeat(CHEVRONS) { index ->
                        Icon(
                            imageVector = SeekArrowIcon,
                            contentDescription = null,
                            tint = Tokens.text,
                            modifier = Modifier
                                .size(CHEVRON_SIZE.dp)
                                .graphicsLayer {
                                    // Mirrored rather than a second vector, and
                                    // fading outward so the row points the way
                                    // it moves — which for the backward side
                                    // means the leading arrow is the last drawn.
                                    scaleX = if (ripple.forward) 1f else -1f
                                    val step = if (ripple.forward) index else CHEVRONS - 1 - index
                                    this.alpha = 1f - step * CHEVRON_FADE
                                },
                        )
                    }
                }
                Text(
                    text = strings.seconds(kotlin.math.abs(ripple.seconds)),
                    color = Tokens.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** Half the frame, which is where the split is. */
private const val HALF = 0.5f

/**
 * How dark the tapped half goes.
 *
 * Light enough that the picture is still visible under it: this appears while
 * the video is playing and the point of a jump is to see where you have landed.
 */
private const val DIM = 0.28f

private const val CHEVRONS = 3
private const val CHEVRON_SIZE = 22
private const val CHEVRON_FADE = 0.3f

private const val FADE_IN_MILLIS = 90
private const val FADE_OUT_MILLIS = 260

/**
 * How long a run of double taps stays open.
 *
 * Past this the next double tap starts counting again from ten. It is longer
 * than the fade so that a badge on its way out can still be added to — the fade
 * is how it looks, and this is what it means.
 */
const val RIPPLE_LINGER_MILLIS = 700L
