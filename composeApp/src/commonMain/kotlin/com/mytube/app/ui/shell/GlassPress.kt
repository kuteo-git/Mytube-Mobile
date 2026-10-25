package com.mytube.app.ui.shell

import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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

    /**
     * How far the control has risen to say "this is where you are", 0 to 1.
     *
     * A remote has no finger, so before anything is pressed something has to
     * say *which* thing a press would land on. That is focus, and it is worn
     * by the same holder as the press for the same reason the press is here:
     * the thing that is painted is the thing that moves, and a container that
     * claims the bloom ([LocalPressHost]) claims this as well.
     */
    internal val focusAnimated = Animatable(0f)

    val focusFraction: Float get() = focusAnimated.value

    /** Whether focus is here at all, for the lift over the neighbours. */
    var focused: Boolean by mutableStateOf(false)
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
                // Launched rather than awaited, unlike the press below. A press
                // is one gesture and the collector may as well wait for it; focus
                // moves on every key of a D-pad held down, and waiting for each
                // rise to finish would leave the highlight a dozen cards behind
                // the one it is on.
                is FocusInteraction.Focus -> {
                    press.focused = true
                    launch { press.focusAnimated.animateTo(1f, FOCUS_SPRING) }
                }

                is FocusInteraction.Unfocus -> {
                    press.focused = false
                    launch { press.focusAnimated.animateTo(0f, FOCUS_SPRING) }
                }

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
fun Modifier.pressSquish(press: GlassPress): Modifier = this.pressLift(press).pressBloom(press)

/**
 * The half of [pressSquish] that lifts the control over its neighbours.
 *
 * Split from the bloom so a focus target can sit *between* them — see
 * [pressable]. The lift has to be outermost to reach the siblings; the target
 * has to be outside the scale, or the bounds it reports are the grown ones.
 */
@Composable
internal fun Modifier.pressLift(press: GlassPress): Modifier {
    val keys = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    return this
        // Read at placement, not in composition: focus moves on every press of
        // the remote, and as `zIndex(...)` it recomposed the control losing
        // focus and the one gaining it each time. Placed again is all it needs.
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0, zIndex = if (press.onTop || (keys && press.focused)) 1f else 0f)
            }
        }
        // Every control with a press comes through here — `pressable`, the
        // glass variants, and the rows and buttons that hold a `GlassPress` of
        // their own — so this is where the [FocusMemory] learns where focus is.
        // It was in `pressable` alone, and Settings' rows, which hold their own
        // press, were never written down: back from a row put focus on the rail.
        .rememberedFocus()
}

/** The half of [pressSquish] that grows the control and draws the focus rim. */
@Composable
internal fun Modifier.pressBloom(press: GlassPress, rimShape: Shape? = null): Modifier {
    // Only when the app is being driven by keys. A finger is its own pointer:
    // a phone showing a rim round the last thing touched would be drawing a
    // cursor nobody asked for, and in touch mode nothing takes focus from a
    // tap anyway. This is the line that keeps the phone exactly as it was.
    val keys = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    return this
        .graphicsLayer {
            // **It grows, and it grows by a distance rather than a ratio.**
            //
            // Both halves of that were wrong once. It shrank, by two per cent —
            // and two per cent of anything is nothing, so the whole animation
            // went unnoticed for weeks. Measured off a screen recording of iOS
            // 26's own glass button: at rest the pane is small and dim, and
            // under a finger it **blooms** to about 1.38 of itself and lights
            // up. Pressing a pane of glass pushes it toward you; it does not
            // dent it.
            //
            // A ratio that large on a button the width of the screen would
            // throw eighty dp of it past its own edges. A *distance* cannot: the
            // same 6dp is a quarter of a small circle and a fiftieth of a wide
            // pill, which is how the platform's own controls behave and why
            // small things feel springy while large ones barely move.
            //
            // The smaller of the two ratios wins so the scale stays uniform — a
            // pill grown by its height alone would stretch, and a control that
            // changes proportion under a finger reads as a rendering fault.
            val outset = PRESS_INSET.toPx() * press.fraction
            val byWidth = if (size.width > 0f) (size.width + outset * 2f) / size.width else 1f
            val byHeight = if (size.height > 0f) (size.height + outset * 2f) / size.height else 1f
            val pressed = minOf(byWidth, byHeight).coerceIn(1f, 1.5f)
            // Focus is a ratio with a ceiling, where the press is a distance:
            // see [focusScale]. The larger of the two wins, so pressing OK on a
            // focused card blooms it from where it already stands rather than
            // snapping it back to rest first.
            // A row the width of its list does not rise at all: the list
            // clips at its own edges, and a full-width settings row grown past
            // them lost both ends of its rim — measured on the television as
            // two white lines and no sides. The rim alone says where focus is.
            val lifted = if (keys && size.width < FOCUS_NO_LIFT_WIDTH.toPx()) {
                1f + (focusScale(size.width, size.height, FOCUS_MAX_OUTSET.toPx()) - 1f) *
                    press.focusFraction
            } else {
                1f
            }
            val scale = maxOf(pressed, lifted)
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            val shown = if (keys) press.focusFraction else 0f
            if (shown <= 0f) return@drawWithContent
            // Inside the node's own bounds, inset by half the stroke: a node is
            // often clipped by a modifier *before* this one — a glyph's circle,
            // a pill — and a rim drawn on the edge would lose its outer half to
            // that clip.
            val stroke = FOCUS_RIM.toPx()
            val inner = Size(size.width - stroke, size.height - stroke)
            val shape = rimShape ?: focusRimShape(size.width, size.height, density)
            translate(stroke / 2f, stroke / 2f) {
                drawOutline(
                    outline = shape.createOutline(inner, layoutDirection, this),
                    color = Color.White.copy(alpha = shown),
                    style = Stroke(width = stroke),
                )
            }
            // And a dark hairline just inside it, because "selected" in this
            // app is a white surface: a white rim on a white row is no rim at
            // all, and the television's settings panel drew a focused, chosen
            // row exactly like a chosen one. On a dark surface the hairline
            // disappears into it and the white rim does the work.
            val hairline = FOCUS_HAIRLINE.toPx()
            val hairInset = stroke + hairline / 2f
            translate(hairInset, hairInset) {
                drawOutline(
                    outline = shape.createOutline(
                        Size(size.width - hairInset * 2f, size.height - hairInset * 2f),
                        layoutDirection,
                        this,
                    ),
                    color = Color.Black.copy(alpha = shown * 0.7f),
                    style = Stroke(width = hairline),
                )
            }
        }
}

/**
 * How much a focused control grows: [FOCUS_SCALE], or less for something big.
 *
 * A ratio, because what is being copied is one: YouTube's TV app lifts the
 * focused card by about a tenth of itself, and a card is the thing a remote
 * spends most of its time on. A ratio alone would carry a full-width settings
 * row seventy dp past the screen's edges, so it is capped by a distance — the
 * same reasoning as the press, arriving from the other side.
 *
 * A named function with a test, because two numbers decide it and the wrong
 * one of `min`/`max` still draws something that looks like focus.
 */
internal fun focusScale(width: Float, height: Float, maxOutset: Float): Float {
    if (width <= 0f || height <= 0f) return 1f
    val byWidth = (width + maxOutset * 2f) / width
    val byHeight = (height + maxOutset * 2f) / height
    return minOf(FOCUS_SCALE, byWidth, byHeight)
}

/**
 * The rim's outline for a control that did not say what shape it is.
 *
 * Small and square, and it is a circle: every small square pressable in this
 * app is a glyph clipped to one. Short and wide, and it is a pill or a row, so
 * the ends are round. Anything taller is a card, and a card's corners are the
 * thumbnail's — *whatever* its proportions. A shelf card is nearly as tall as
 * it is wide, and the first build on the television ringed every one of them
 * with a circle.
 */
internal fun focusRimShape(width: Float, height: Float, density: Float): Shape {
    if (width <= 0f || height <= 0f) return RoundedCornerShape(0.dp)
    val short = height <= PILL_MAX_HEIGHT.value * density
    if (short && width / height in SQUARE_RANGE) return CircleShape
    if (short) return RoundedCornerShape(percent = 50)
    return RoundedCornerShape(CARD_RIM_RADIUS)
}

/** How close to square a control is to be ringed as a circle. */
private val SQUARE_RANGE = 0.87f..1.15f

/** The tallest control ringed as a pill rather than as a card. */
private val PILL_MAX_HEIGHT = 64.dp

/** A card's corners — [com.mytube.app.ui.home.Radius.thumbnail]'s number. */
private val CARD_RIM_RADIUS = 12.dp

/** How far a focused control may grow, as a ratio of itself. */
internal const val FOCUS_SCALE = 1.08f

/** …and the most it may grow past each edge, whatever the ratio says. */
val FOCUS_MAX_OUTSET: Dp = 16.dp

/** At this width or wider a focused control keeps its size and wears the rim only. */
private val FOCUS_NO_LIFT_WIDTH = 480.dp

/** The focus rim's width. Thick enough to read from a sofa. */
private val FOCUS_RIM = 3.dp

/** The dark line inside the rim that keeps it visible on a white surface. */
private val FOCUS_HAIRLINE = 1.5.dp

/**
 * Quick and without a wobble. Focus is not a finger being let go — it is a
 * cursor arriving, and a cursor that overshoots reads as one that missed.
 */
private val FOCUS_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** How far a pressed control blooms past each edge. */
val PRESS_INSET = 6.dp

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
): Modifier = pressableMaterial(shape, enabled, onClick) { glassControl(shape, selected = selected, press = it) }

/** The same, for the sampled material — a chip, the search field. */
@Composable
fun Modifier.pressableLiquidGlass(
    shape: CornerBasedShape,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = pressableMaterial(shape, enabled, onClick) { liquidGlass(shape, selected = selected, press = it) }

/**
 * The same, for a control over the video, which is painted from black — or,
 * [selected], the design system's selected control, the inverted surface.
 *
 * One modifier for both states rather than a choice between this and
 * [pressableGlassControl]: two modifiers are two remembered presses and two
 * focus targets, and the television's narration button, switched on under the
 * remote, threw away the one wearing focus — the rim went with it.
 */
@Composable
fun Modifier.pressableGlassSurface(
    shape: Shape,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = pressableMaterial(shape, enabled, onClick) {
    if (selected) glassControl(shape, selected = true, press = it) else glassSurface(shape, press = it)
}

/**
 * Lift, target, bloom — [pressable]'s order and for its reasons — with the
 * [material] inside the bloom, so the pane is what grows. The three glass
 * variants differ only in that material.
 */
@Composable
private fun Modifier.pressableMaterial(
    shape: Shape,
    enabled: Boolean,
    onClick: () -> Unit,
    material: @Composable Modifier.(GlassPress) -> Modifier,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    return this
        .pressLift(press)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
        .pressBloom(press, rimShape = shape)
        .material(press)
}

/**
 * Anything that answers a finger and draws no pane of its own.
 *
 * A row in a menu, a card, a bare glyph, one half of a joined pill. They get
 * the movement and nothing else — no surface, because they are content sitting
 * on somebody else's pane, and lighting a second surface under them is the
 * selected-state fault in a new place.
 *
 * It is safe to put on anything, and that is a property of [pressSquish] rather
 * than of judgement: the bloom is a *distance*, so a full-width row grows by
 * one per cent while a 40dp circle grows by a fifth. Large things barely move
 * without anyone having to decide that they should.
 *
 * It is also what a remote lands on. `clickable` is focusable and answers OK
 * and Enter as a click, so every control that goes through here is reachable
 * from a D-pad without saying so — and the rise and rim focus wears are drawn
 * by [pressSquish] on the same holder. That is why the TV needed no second
 * kind of button.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    /**
     * The focus rim's outline, for a control whose shape the size cannot tell —
     * a card is about as tall as it is wide, and is not a circle. Null leaves it
     * to [focusRimShape].
     */
    rimShape: Shape? = null,
    /**
     * A long press — of a finger, or of a remote's OK, which `combinedClickable`
     * times the same way. On a television it is the one way to a card's menu:
     * a card there has no dots to aim at. Null for none.
     */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    // A container above may have claimed the bloom — see [LocalPressHost].
    val host = LocalPressHost.current
    val own = remember { MutableInteractionSource() }
    val press = rememberGlassPress(own)
    val source = host ?: own
    // `clickable` when there is no long press, so every control that has none
    // — all of them on a phone — answers exactly as it always has.
    val click = if (onLongClick == null) {
        Modifier.clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        Modifier.combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
    if (host != null) return this.then(click)
    // The lift, then the target, then the bloom — the target between them.
    // On a television the target also says when it gains focus — see
    // [FocusMemory].
    // Focus asks the list it is in to scroll it into view using the target's
    // bounds, and a target inside the scale reported them part-way grown: with
    // every press of left or right on a television shelf, every row on the page
    // rose four to six dp and settled back, measured frame by frame off a
    // screen recording — the size of a card part-way through its rise. Drawn,
    // the order changes nothing.
    return this
        .pressLift(press)
        .then(click)
        .pressBloom(press, rimShape)
}

/**
 * Write this control into the [FocusMemory] when it gains focus, where there is
 * one. Nothing at all where there is not — on a phone.
 *
 * Before the focus target in the chain: a `focusRequester` names the target
 * that follows it.
 */
@Composable
internal fun Modifier.rememberedFocus(): Modifier {
    val memory = LocalFocusMemory.current ?: return this
    val requester = remember { FocusRequester() }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) memory.focused(requester) }
}

/**
 * The container that blooms instead of the control inside it, or null.
 *
 * # The rule this exists to enforce: what blooms is what is *painted*
 *
 * Every press in this app used to scale the node it was written on, and for a
 * control that draws its own pane — a chip, an action pill, a card — that node
 * *is* the pane, so the right thing happened. For a control whose pane is drawn
 * by something above it, the node holds a glyph and nothing else, so all a
 * press could ever move was the glyph.
 *
 * Reported from the phone in three places at once and they turned out to be one
 * fault: *"bấm vào Like dislike thì icon zoom, nó phải zoom cái button cơ"*,
 * the same for the player's controls, and *"bấm vào item thì ko zoom icon,
 * phải zoom toàn bộ cái tabbar như media mini player"* — which is the same
 * sentence said about the third place, and is the clearest statement of the
 * rule: **the pill is the button, so the pill is what moves.**
 *
 * Measured on the emulator before any of it was changed, holding a touch with
 * `input motionevent DOWN` and taking the bounding box of what moved: Like's
 * box was 55×55 at (83,1171), which is the *icon's* own bounds to the pixel,
 * while Share — which draws its own pane — measured 277×117.
 *
 * So a container declares itself the host, every [pressable] inside routes its
 * interactions there instead of squashing itself, and the host wears the bloom
 * for all of them. Nothing at the call sites changed: a control keeps asking
 * for a press exactly as it did, and where it sits decides what grows.
 */
val LocalPressHost = compositionLocalOf<MutableInteractionSource?> { null }

/**
 * Claim the bloom for this node on behalf of everything drawn inside it.
 *
 * Two parts because a modifier cannot provide a composition local: put this on
 * the node that paints the pane, and wrap its content in
 * `CompositionLocalProvider(LocalPressHost provides source)` with the same
 * source.
 */
@Composable
fun Modifier.pressHost(source: MutableInteractionSource): Modifier =
    this.pressSquish(rememberGlassPress(source))

/**
 * Somewhere a remote can stop that does nothing when OK is pressed.
 *
 * A comment, a paragraph of a description: a list a D-pad has to be able to
 * move through, where nothing in it is a control. A list scrolls by moving focus,
 * so a column with nothing focusable in it cannot be read past its first screen
 * — and making each row a button would be a dead button wearing a comment.
 *
 * It wears focus the way a control does, through the same holder, so being on a
 * comment looks like being on a card.
 */
@Composable
fun Modifier.focusStop(
    /** The rim's outline where the size cannot tell it — see [pressable]. */
    rimShape: Shape? = null,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    // Lift, target, bloom, for [pressable]'s reason.
    return this
        .pressLift(press)
        .focusable(interactionSource = source)
        .pressBloom(press, rimShape)
}
