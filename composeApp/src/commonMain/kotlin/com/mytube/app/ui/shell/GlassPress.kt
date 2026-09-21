package com.mytube.app.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * How far a control is pressed, from 0 to 1.
 *
 * # Why glass has to move when it is touched
 *
 * Every control in this app is a pane of glass, and until now pressing one did
 * nothing at all: `indication = null` everywhere, because Material's ripple is
 * ink spreading through paper and this app is not made of paper. That removed
 * the wrong answer and left no answer — a button with no acknowledgement is one
 * people press twice.
 *
 * What glass does under a finger is **give**. The pane squashes a little, its
 * refraction deepens because the sheet is now thicker where it is pushed, and it
 * springs back past its resting size when the finger lifts. That is one movement
 * of the whole pane, not a mark travelling across it.
 *
 * # Why this is a holder rather than a modifier
 *
 * Because two different things read it and they are in different places. The
 * *layout* reads it to scale the node, and the *material* reads it to deepen the
 * lens — and the material is drawn inside `drawBackdrop`, whose parameters are
 * all lambdas evaluated at draw time. Passing the same object to both is what
 * keeps them in step; a modifier could only do the first half.
 *
 * Reading [fraction] inside a draw lambda subscribes that draw to it, so a press
 * costs a redraw and no recomposition.
 */
@Stable
class GlassPress internal constructor() {
    internal val animated = Animatable(0f)

    val fraction: Float get() = animated.value

    /**
     * Whether this control should draw over the ones beside it.
     *
     * A pressed pane blooms past its own edges, and a sibling drawn after it
     * covers the part that has grown — a tab pressed on the left came out with
     * a bite taken from its right-hand side. So a press lifts the control for
     * as long as the movement lasts.
     *
     * A boolean rather than the fraction, and that is the whole point: `zIndex`
     * is read in composition, so a value that changed every frame would
     * recompose the control sixty times a second to move something the draw
     * phase is already moving. This turns over twice per press.
     */
    var onTop: Boolean by mutableStateOf(false)
        internal set
}

/**
 * A [GlassPress] driven by an interaction source.
 *
 * Springs, not tweens, and two different ones: pressing is quick and damped
 * because it should feel like meeting the surface, and releasing overshoots
 * because that is what a sheet under tension does when it is let go. A single
 * symmetric curve reads as an animation playing rather than as a material
 * responding.
 */
@Composable
fun rememberGlassPress(interactionSource: InteractionSource): GlassPress {
    val press = remember { GlassPress() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                // Down fast and a little past, which is the half nobody
                // notices and everybody feels: measured off a screen recording
                // of iOS 26's own glass button, the mark dips to 0.67 and
                // settles back to 0.72 while the finger is still on it. A
                // critically damped press arrives at its value and stops, which
                // reads as a control being *set* rather than one being met.
                is PressInteraction.Press -> {
                    press.onTop = true
                    press.animated.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh,
                    ),
                    )
                }

                // And back with a wobble, which is the part a finger remembers.
                //
                // `LowBouncy` rather than `MediumBouncy`: the pane has just
                // bloomed a fifth of its own width, and a release that merely
                // eases back to rest throws all of that away silently. Two or
                // three visible oscillations is a sheet of glass under tension
                // being let go, and it is the half of the movement that says
                // the press was *received* rather than only seen.
                //
                // `StiffnessMedium` keeps the whole thing inside a couple of
                // hundred milliseconds, so it settles before a second tap.
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    press.animated.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                    // Put back only once the wobble has finished, or the last
                    // few oscillations would be drawn under the neighbour again.
                    press.onTop = false
                }
            }
        }
    }
    return press
}

/**
 * The squash, applied to whatever the control is.
 *
 * `graphicsLayer` with a lambda rather than `scale()`: the lambda is read at
 * *draw* time, so an animating press never recomposes the control it is on. On a
 * chip row that matters — the alternative is recomposing a `LazyRow` item at
 * 120fps to move it 2%.
 *
 * **Two per cent, and it is deliberately small.** A control that visibly shrinks
 * reads as a lever; this has to say "the surface took your finger" without the
 * pane leaving the place it was pressed. The card in the feed already settles by
 * the same amount, and that number was measured against the YouTube app.
 */
@Composable
fun Modifier.pressSquish(press: GlassPress): Modifier = this
    .zIndex(if (press.onTop) 1f else 0f)
    .graphicsLayer {
    // **It grows, and it grows by a distance rather than a ratio.**
    //
    // Both halves of that were wrong once. It shrank, by two per cent — and two
    // per cent of anything is nothing, so the whole animation went unnoticed for
    // weeks. Measured off a screen recording of iOS 26's own glass button: at
    // rest the pane is small and dim, and under a finger it **blooms** to about
    // 1.38 of itself and lights up. Pressing a pane of glass pushes it toward
    // you; it does not dent it.
    //
    // A ratio that large on a button the width of the screen would throw eighty
    // dp of it past its own edges. A *distance* cannot: the same 6dp is a
    // quarter of a small circle and a fiftieth of a wide pill, which is how the
    // platform's own controls behave and why small things feel springy while
    // large ones barely move.
    //
    // The smaller of the two ratios wins so the scale stays uniform — a pill
    // grown by its height alone would stretch, and a control that changes
    // proportion under a finger reads as a rendering fault.
    val outset = PRESS_INSET.toPx() * press.fraction
    val byWidth = if (size.width > 0f) (size.width + outset * 2f) / size.width else 1f
    val byHeight = if (size.height > 0f) (size.height + outset * 2f) / size.height else 1f
    val scale = minOf(byWidth, byHeight).coerceIn(1f, 1.5f)
        scaleX = scale
        scaleY = scale
    }

/** How far a pressed control blooms past each edge. */
internal val PRESS_INSET = 6.dp

/**
 * How much deeper the glass refracts while it is held.
 *
 * A multiplier on the lens, not a second lens: pressing a real pane does not add
 * an effect to it, it changes how much the one it has bends what is behind.
 */
internal const val PRESS_LENS_GAIN = 0.6f

/**
 * A pressable pane of glass: the material, the squash and the click, together.
 *
 * One modifier because they are one thing. Written apart — `.glassControl(shape)`
 * beside `.clickable { }` — a control gets its material from one line and its
 * behaviour from another, and the next control written is the one that gets the
 * material and no press. That is how this app ended up with fifteen glass
 * controls and none of them acknowledging a finger.
 *
 * `indication = null` stays: Material's ripple is ink spreading through paper,
 * and what replaces it is [pressSquish] on the same interaction source.
 */
@Composable
fun Modifier.pressableGlassControl(
    shape: Shape,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    return this
        .pressSquish(press)
        .glassControl(shape, selected = selected, press = press)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** The same, for the sampled material — a chip, the search field. */
@Composable
fun Modifier.pressableLiquidGlass(
    shape: CornerBasedShape,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    return this
        .pressSquish(press)
        .liquidGlass(shape, selected = selected, press = press)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** The same, for a control over the video, which is painted from black. */
@Composable
fun Modifier.pressableGlassSurface(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    return this
        .pressSquish(press)
        .glassSurface(shape, press = press)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
