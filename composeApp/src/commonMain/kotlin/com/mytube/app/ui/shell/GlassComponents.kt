package com.mytube.app.ui.shell

import androidx.compose.foundation.background
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
     * A panel with content in rows — a menu, a card of settings.
     *
     * Round enough to belong beside the capsules, square enough that a list of
     * rows inside it does not have its first and last item clipped by the curve.
     */
    val panel: CornerBasedShape = RoundedCornerShape(20.dp)

    /**
     * A sheet's top corners.
     *
     * Larger than [panel] because a sheet is the width of the screen: the same
     * radius that reads as generous on a 200dp menu reads as almost square on a
     * 390dp sheet.
     */
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

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
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
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
            textStyle = LocalTextStyle.current.merge(
                TextStyle(color = Tokens.text, fontSize = 15.sp, textAlign = textAlign),
            ),
            cursorBrush = SolidColor(Tokens.brand),
            modifier = Modifier.fillMaxWidth(),
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
    Box(
        modifier
            .height(BUTTON_HEIGHT)
            .glassControl(GlassRadius.control, selected = primary && enabled)
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
                    Modifier.pointerInput(onClick) { detectTapGestures { onClick() } }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = if (primary) Tokens.invertText else Tokens.text,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = label,
                color = if (primary && enabled) Tokens.invertText else Tokens.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private val BUTTON_HEIGHT = 48.dp

/**
 * What a popup is made of, since it cannot be made of glass.
 *
 * A menu lives in its own layer with its own coordinate space, so a sampled
 * backdrop there reads the wrong slice of the app — and its anchor is inside the
 * layer the shell records, where a sampled backdrop is not a wrong picture but a
 * crash (see `PullGlass`). Both roads are closed, so this one is honest about
 * being paint.
 *
 * **Dark, and nearly solid.** [glassControl] was tried first and it is built for
 * a control *on* a page: white at 0.09, which over a bright thumbnail turns the
 * panel into a grey smear with sharp video showing through it. A menu is a sheet
 * of the app's own surface that happens to be see-through — the theme's colour at
 * 0.92, with the same hairline the panes carry, so it belongs to the set without
 * pretending to sample anything.
 */
fun Modifier.menuSurface(shape: CornerBasedShape): Modifier = this
    .clip(shape)
    .background(Tokens.surface.copy(alpha = MENU_ALPHA))
    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)

private const val MENU_ALPHA = 0.92f
