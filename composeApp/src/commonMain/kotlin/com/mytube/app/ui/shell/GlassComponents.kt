package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import com.mytube.app.ui.home.Space
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.theme.Tokens

/**
 * The controls this app is made of, in one material.
 *
 * # Why this file exists
 *
 * The glass arrived one surface at a time — the bars, then the player, then the
 * chips — and everything the platform's own widgets drew stayed where it was: a
 * Material slider with a gap in its track, a Material text field with a box
 * around it, a Material dropdown with a 4dp corner, a pull-to-refresh disc in
 * the default grey. Beside a floating capsule of glass, each of those reads as
 * a control borrowed from another app, which is what was reported.
 *
 * So the shapes and the surfaces live here, and a screen asks for a control
 * rather than for a Material one it then has to talk out of its own appearance.
 * The next time the material changes, it changes in this file.
 *
 * # The two materials, and which one a control gets
 *
 * [Modifier.liquidGlass] samples what is behind it and belongs to anything drawn
 * **over** the shell's recorded layer — the bars, the chips, the sheet.
 * [Modifier.glassControl] is paint, and belongs to everything **inside** that
 * layer, which would otherwise be sampling a recording of itself.
 *
 * A control in a **popup** — a dropdown menu — is paint too, and for a third
 * reason worth writing down: a popup renders in its own layer with its own
 * coordinate space, so a sampled backdrop there draws the slice of the app from
 * the *top of the screen*. That is the failure `SheetBackdrop.kt` recorded and
 * `GlassSheet` was built to escape; a menu is small enough that paint is the
 * right answer rather than another escape.
 */
object GlassRadius {

    /** Anything shaped like a pill: chips, buttons, fields, the bars. */
    val control: CornerBasedShape = RoundedCornerShape(percent = 50)

    /**
     * A panel somebody stops at and answers — an alert, a card of settings.
     *
     * [PANEL_RADIUS], the same as [sheet]: the two are one idea at two sizes,
     * and two radii for one idea is a seam a reader notices without being able
     * to name it — the lesson `TINT_GLASS` cost when the miniplayer and the tab
     * bar carried two tints.
     */
    val panel: CornerBasedShape = RoundedCornerShape(PANEL_RADIUS)

    /**
     * A dropdown menu, and the one shape that is **not** [PANEL_RADIUS].
     *
     * Four dp short of it, because a menu is narrow: the curve that reads as
     * generous across a 390dp sheet arrives while the first row's text is still
     * there on a 200dp menu. A difference in width, not a second opinion about
     * the shape — and the only exception, written here so the next panel does
     * not copy it.
     */
    val menu: CornerBasedShape = RoundedCornerShape(28.dp)

    /**
     * A sheet's corners — all four of them.
     *
     * All four because the sheet floats inset from the edges rather than sitting
     * flush against them, like every other pane of glass in this app.
     *
     * **36dp, and it follows the margin.** The same iOS screenshot the sheet's
     * inset was measured from gives its corner too: the left edge reaches its
     * straight run 52px below the pane's top, which at 1.5 px per point is a
     * radius of about 35pt. That is not a coincidence — an iPhone 16e's display
     * corner is 47.33pt and the sheet sits 10pt inside it, so 47.33 − 10 ≈ 37 is
     * the concentric answer and the platform is drawing it.
     *
     * Larger than [panel] for that reason rather than by taste: an alert is
     * centred and far narrower, so it is nowhere near the screen's corner and
     * has nothing to be concentric with.
     */
    val sheet: CornerBasedShape = RoundedCornerShape(36.dp)
}

/**
 * The one radius everything square-ish in this app is drawn with.
 *
 * Concentric with an iPhone 16e's 47.33pt display corner across the 16dp margin
 * a floating pane keeps — see [GlassRadius.sheet].
 */
val PANEL_RADIUS = 32.dp

/**
 * A slider made of the same glass as everything else.
 *
 * Replaces both Material's `Slider` and the hand-rolled `LevelSlider` that was
 * written to escape it. That one already made the right argument — *"one bar,
 * filled to the value. Not two bars with a gap between them, which is Material
 * 3's shape and the thing that reads as foreign here"* — and then drew the bar
 * in a flat surface colour, which is the same argument left half finished.
 *
 * - **The track is glass**, so it shows the page through it the way every other
 *   control on it does.
 * - **The knob is a solid disc**, not glass. It is the one part of this control
 *   that has to be found without looking, and a translucent knob over a
 *   translucent track is two panes that disappear into each other.
 * - **44dp of target around a 4dp line.** The line is what is drawn; the height
 *   is what a thumb can hit, and 4dp is nothing to aim at.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    BoxWithConstraints(modifier.fillMaxWidth().height(SLIDER_TOUCH)) {
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
                // Both gestures read the same node, so a tap lands where the
                // knob is drawn rather than a knob's width away.
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SLIDER_TRACK)
                    .glassControl(GlassRadius.control),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(GlassRadius.control)
                        .background(Tokens.brand),
                )
            }

            // Centred on the value, and allowed to reach the very ends: the
            // offset is measured from the track's start, so a knob at 0 sits
            // half outside it — which is what every platform draws and what
            // makes "off" look like off.
            Box(
                Modifier
                    .offset(x = width * fraction - SLIDER_KNOB / 2)
                    .size(SLIDER_KNOB)
                    .clip(GlassRadius.control)
                    .background(Tokens.text),
            )
        }
    }
}

/**
 * A text field with no box around it.
 *
 * Material's `OutlinedTextField` draws a rectangle with a notched border and a
 * label that moves — a shape from a different design language, and one that
 * cannot be talked into being a capsule. This is `BasicTextField` on a pane of
 * glass, which is what the search field in the top bar has always looked like.
 *
 * The placeholder is drawn underneath rather than as a floating label: there is
 * one field per screen here and what it wants is written above it.
 */
/**
 * The same field, driven by a [TextFieldValue] so the caller owns the caret.
 *
 * A `String`-valued `BasicTextField` puts the caret at position **zero** when it
 * is focused with text already in it — measured on the rename alert, where the
 * first letter typed landed in front of the name being edited. Only the caller
 * knows where the caret belongs, so the value it selects with is the value it
 * passes.
 */
@Composable
fun GlassTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    focusRequester: FocusRequester? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(FIELD_HEIGHT)
            .glassControl(GlassRadius.control)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                color = Tokens.text2,
                fontSize = 15.sp,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = LocalTextStyle.current.merge(
                TextStyle(color = Tokens.text, fontSize = 15.sp, textAlign = textAlign),
            ),
            cursorBrush = SolidColor(Tokens.brand),
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    /** What the return key does. Default is nothing, which is what it did. */
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /**
     * Where to send focus from, for a caller that wants the cursor here on
     * arrival. On the field rather than on [modifier], which lands on the pane
     * around it — a `focusRequester` there requests focus for a `Box`, which
     * takes it and never raises a keyboard.
     */
    focusRequester: FocusRequester? = null,
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(FIELD_HEIGHT)
            .glassControl(GlassRadius.control)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Tokens.text2,
                fontSize = 15.sp,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = LocalTextStyle.current.merge(
                TextStyle(color = Tokens.text, fontSize = 15.sp, textAlign = textAlign),
            ),
            cursorBrush = SolidColor(Tokens.brand),
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        )
    }
}

/**
 * What a pull-to-refresh shows instead of Material's disc.
 *
 * The default is a grey circle with a shadow under it, which is the one thing on
 * these screens with a drop shadow at all. This is the app's own pane: a glass
 * disc with the ring inside it, and it grows with the pull rather than appearing
 * at full size.
 *
 * `fraction` is the pull's progress and `refreshing` is whether the request is
 * in flight — two facts, because a pull released at 30% has to shrink away and a
 * pull released past the threshold has to stay while the feed loads.
 */
@Composable
fun GlassRefreshIndicator(
    fraction: Float,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = if (refreshing) 1f else fraction.coerceIn(0f, 1f)
    if (scale <= 0.01f) return

    Box(
        modifier
            .size(REFRESH_SIZE * scale)
            // The sampled material. Legal here and nowhere near the list,
            // because `AppShell` draws this — see [PullGlass].
            .liquidGlass(GlassRadius.control),
        contentAlignment = Alignment.Center,
    ) {
        // A determinate ring while the finger is still deciding — it is the one
        // honest drawing of "this far and it will refresh" — and a spinning one
        // once there is something to wait for. Two overloads rather than a
        // nullable progress, because that is what Material gives.
        if (refreshing) {
            CircularProgressIndicator(
                color = Tokens.text,
                strokeWidth = 2.dp,
                modifier = Modifier.size(REFRESH_RING * scale),
            )
        } else {
            CircularProgressIndicator(
                progress = { scale },
                color = Tokens.text,
                strokeWidth = 2.dp,
                modifier = Modifier.size(REFRESH_RING * scale),
            )
        }
    }
}

private val SLIDER_TOUCH = 44.dp
private val SLIDER_TRACK = 4.dp
private val SLIDER_KNOB = 20.dp
private val FIELD_HEIGHT: Dp = 48.dp
private val REFRESH_SIZE = 40.dp
private val REFRESH_RING = 20.dp

/**
 * A button, in the two weights this app has.
 *
 * `primary` is the app's inverted surface — solid and light, dark text on it,
 * the same thing a selected chip is made of. Everything else is a pane of glass
 * with the ordinary text colour on it. Two weights and no third: a screen with
 * three kinds of button is a screen where none of them means anything.
 *
 * Material's `Button` and `OutlinedButton` were what this replaced. They carry a
 * container colour, a border, an elevation and a ripple from a design language
 * that is not this one, and the setup screen — the first thing anybody sees —
 * was drawing both of them.
 */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    // The one call to action on a screen is the brand's red, not a pane of
    // glass and not the inverted surface it used to be.
    //
    // The inverted surface is what *selected* means everywhere else in this app
    // — a chip, a tick row, the Save pill — so a button wearing it said "this
    // is on" rather than "press this". Red says the second thing and says it in
    // the colour the web app and YouTube both use for it. Everything that is
    // not the call to action stays glass, which is what makes one of them the
    // call to action.
    val filled = primary && enabled
    // The squash is on both weights. A red button is not made of glass and still
    // has to answer a finger — what changes with the material is what *else*
    // happens, not whether the control moves.
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    Box(
        modifier
            .height(BUTTON_HEIGHT)
            .pressSquish(press)
            .then(
                if (filled) {
                    Modifier.clip(GlassRadius.control).background(Tokens.brand)
                } else {
                    Modifier.glassControl(GlassRadius.control, press = press)
                },
            )
            // Inside the pane, so a button sized by its label is a capsule
            // rather than a circle with the word hanging out of both ends.
            // Measured: "Play all" on a playlist page, before this line.
            // A button given `fillMaxWidth` is unaffected — the padding then
            // only insets its text.
            .padding(horizontal = 20.dp)
            // A disabled button is dimmed rather than removed, and it still
            // occupies its place: the row it is in must not reflow when a field
            // becomes valid.
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (enabled && !loading) {
                    // `clickable` rather than the tap detector it had, because
                    // the press animation reads the interaction source and a raw
                    // `detectTapGestures` publishes nothing to one.
                    Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Tokens.text,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = label,
                color = Tokens.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * A pill with a mark on it — the shape the watch page's actions are.
 *
 * Here rather than beside those actions because the playlist page needed the
 * same one, and a second copy is how two rows of controls in one app come to
 * disagree about their padding. `WatchActions` draws its row through this.
 *
 * **Selected is a change of kind, and the content follows it.** `glassControl`
 * swaps to the app's inverted surface — solid and light — so white ink on it
 * disappears: the Save pill in its saved state was a blank white capsule with
 * an invisible bookmark on it, which is how it was reported.
 */
@Composable
fun GlassPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** Vertically mirrored, for a thumb pointing the other way. */
    flipped: Boolean = false,
) {
    val ink = if (selected) Tokens.invertText else Tokens.text
    Row(
        modifier
            .pressableGlassControl(GlassRadius.control, selected = selected, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ink,
            modifier = Modifier
                .size(20.dp)
                .scale(scaleX = 1f, scaleY = if (flipped) -1f else 1f),
        )
        Spacer(Modifier.width(Space.sm))
        Text(label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private val BUTTON_HEIGHT = 48.dp

