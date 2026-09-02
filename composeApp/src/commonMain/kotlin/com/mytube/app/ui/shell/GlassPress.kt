package com.mytube.app.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

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
                is PressInteraction.Press -> press.animated.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessHigh,
                    ),
                )

                is PressInteraction.Release, is PressInteraction.Cancel ->
                    press.animated.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
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
fun Modifier.pressSquish(press: GlassPress): Modifier = this.graphicsLayer {
    val scale = 1f - PRESS_SQUASH * press.fraction
    scaleX = scale
    scaleY = scale
}

internal const val PRESS_SQUASH = 0.02f

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
