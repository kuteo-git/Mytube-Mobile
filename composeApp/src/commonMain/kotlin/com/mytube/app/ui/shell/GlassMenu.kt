package com.mytube.app.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * One row of an overflow menu.
 *
 * A label and what pressing it does. Nothing about where it is drawn, because
 * the card that offers it does not know — see [MenuHost].
 */
data class MenuAction(val label: String, val onClick: () -> Unit)

/**
 * The one menu that can be open, and where it is anchored.
 *
 * # Why this is a global rather than a popup
 *
 * A menu used to be Material's `DropdownMenu`, which is paint rather than glass,
 * and the file that said so gave two reasons that were both true:
 *
 * - **A popup renders in its own layer with its own coordinate space**, so a
 *   sampled backdrop there draws the slice of the app from the top of the
 *   screen. That is the failure `SheetBackdrop.kt` recorded and `GlassSheet` was
 *   built to escape.
 * - **The anchor sits inside the layer the shell records**, where a sampled
 *   backdrop is not a wrong picture but a segfault — `PullGlass` and
 *   `GlassAlert` each paid for that one.
 *
 * Both are about *where the menu is drawn*, and neither is about a menu. So the
 * menu is drawn where the sheets and the alert are: as an ordinary child of the
 * root `Box`, outside the recording, at real coordinates. The card keeps the
 * button and publishes the rectangle it landed on; `App.kt` draws the rest.
 *
 * **One menu at a time is not a simplification, it is what a menu is.** Opening
 * a second closes the first on every platform, so a single slot cannot be wrong
 * — and it is what lets a card, which is deep inside a list, hand its menu to
 * the root of the app without threading a callback through four screens.
 */
object MenuHost {
    var anchor by mutableStateOf(Rect.Zero)
        private set

    /**
     * Where the pane ended up, so a press can be told inside from outside.
     *
     * Written by [GlassMenu] once it has been placed, read by
     * [dismissMenuOnOutsidePress] — which lives on the root of the app and
     * therefore cannot see the pane any other way.
     */
    var pane by mutableStateOf(Rect.Zero)
        internal set
    val actions = mutableStateListOf<MenuAction>()
    var open by mutableStateOf(false)
        private set

    fun show(at: Rect, items: List<MenuAction>) {
        if (items.isEmpty()) return
        anchor = at
        actions.clear()
        actions.addAll(items)
        open = true
    }

    fun dismiss() {
        open = false
    }
}

/**
 * The menu itself, drawn at the root and made of the same glass as the bars.
 *
 * Placed by hand, because nothing places it any more: it hangs from the anchor's
 * **bottom right**, which is where a menu opened by a button on the right of a
 * row belongs, and flips above the anchor when there is not room below. A popup
 * did this for free and charged the material for it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.GlassMenu() {
    // Kept while it plays its exit, the same reason [GlassSheet] keeps its
    // target: an `if` around the node removes the thing the exit animates on, so
    // the menu would vanish rather than fade.
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = MenuHost.open
    if (!shown.targetState && !shown.currentState) return

    BackHandler(MenuHost.open) { MenuHost.dismiss() }

    val density = LocalDensity.current
    val container = LocalWindowInfo.current.containerSize
    var size by remember { mutableStateOf(IntOffset.Zero) }

    val margin = with(density) { Space.md.toPx() }
    val gap = with(density) { Space.xs.toPx() }
    val anchor = MenuHost.anchor

    // Right edges aligned, then pulled back inside the screen. Aligning the left
    // edge instead would put a 200dp panel off the side of the phone for a
    // button that sits 8dp from it.
    val x = (anchor.right - size.x)
        .coerceIn(margin, (container.width - size.x - margin).coerceAtLeast(margin))
    // Below when it fits, above when it does not. The flip is the whole of what
    // a popup's position provider was doing that is worth keeping: a menu opened
    // on the last card of a list would otherwise be drawn off the bottom.
    val below = anchor.bottom + gap
    val y = if (below + size.y <= container.height - margin) {
        below
    } else {
        (anchor.top - gap - size.y).coerceAtLeast(margin)
    }

    // Published for the root's watcher — see [dismissMenuOnOutsidePress].
    MenuHost.pane = Rect(x, y, x + size.x, y + size.y)

    // Which corner of the pane the button is at, as a fraction of the pane.
    //
    // A menu that grows from its own centre reads as a panel appearing over the
    // page; one that grows from the button reads as the button becoming the
    // menu. The anchor is already known — it is what places the pane — so the
    // origin costs nothing but this arithmetic, and it stays correct when the
    // pane flips above the button rather than below it.
    val origin = TransformOrigin(
        pivotFractionX = if (size.x > 0) ((anchor.center.x - x) / size.x).coerceIn(0f, 1f) else 1f,
        pivotFractionY = if (y >= anchor.bottom) 0f else 1f,
    )

    AnimatedVisibility(
        visibleState = shown,
        // Springs, not tweens, and it overshoots: the same movement a pressed
        // control makes when it is let go — see [GlassPress]. A menu that eases
        // in linearly is an animation playing; one that arrives past its size
        // and settles is a thing made of something.
        enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) +
            scaleIn(
                initialScale = 0.85f,
                transformOrigin = origin,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        exit = fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = origin),
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
    ) {
        Box(
            Modifier
                // Wrapped to its widest row, as a menu is everywhere.
                //
                // It had a minimum width, which made a two-word menu a slab the
                // size of a three-word one — a popup wraps, and this replaced a
                // popup. Only the cap stays: a label long enough to reach it is
                // one that should wrap rather than run off the phone.
                .widthIn(max = MAX_WIDTH)
                .onSizeChanged { size = IntOffset(it.width, it.height) },
        ) {
            // Real glass: this node is a sibling of the recording, so it may
            // sample it. That is the whole change.
            GlassBackdrop(
                Modifier.matchParentSize(),
                LocalBackdrop.current,
                fromTop = true,
                shape = GlassRadius.menu,
                // The bars' tone, not the sheet's.
                //
                // A sheet is a surface somebody stops at, and its 0.95 keeps the
                // feed behind it from competing with its own rows. A menu is
                // three lines opened over a page still being read, and at that
                // tint it is a black rectangle with a rim — which is what the
                // paint version was, and the reason for moving it. Seeing
                // through it is the point.
                //
                // Legibility comes from the blur and the vibrancy rather than
                // from the tint: the tab bar's 10sp labels hold at this value
                // over the same thumbnails these rows cross.
                tint = TINT_GLASS,
            )
            // `IntrinsicSize.Max` so the rows may fill a pane that is itself
            // only as wide as the widest of them. Without it `fillMaxWidth`
            // inside a wrapped column measures against the screen, and a
            // two-item menu becomes the width of the phone.
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .padding(vertical = EDGE_PADDING),
            ) {
                MenuHost.actions.forEach { action ->
                    // The row squashes under a finger like every other control
                    // — one movement for one gesture, whatever it is on.
                    val source = remember { MutableInteractionSource() }
                    val press = rememberGlassPress(source)
                    Box(
                        Modifier
                            .pressSquish(press)
                            // The whole row, not the width of its label. A menu
                            // item that only answers where the letters are is
                            // one people press twice — and the empty half of the
                            // row is exactly where a thumb lands.
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .clickable(
                                interactionSource = source,
                                indication = null,
                            ) {
                                // Closed first. An action that opens a sheet
                                // would otherwise leave the menu standing under
                                // it, and the sheet is drawn later in the same
                                // Box — so it would be a menu on top of the
                                // thing it opened.
                                MenuHost.dismiss()
                                action.onClick()
                            }
                            .padding(horizontal = Space.lg),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = action.label,
                            color = Tokens.text,
                            fontSize = 15.sp,
                            // Without this the row's ink sits high in it.
                            //
                            // A `Text` with no line height carries the font's
                            // own leading, and for 15sp that is about 21sp with
                            // more slack under the glyphs than over them — so a
                            // row padded evenly *looks* padded unevenly, which
                            // is how it was reported. The tab bar's 10sp labels
                            // had the same fault and the same fix.
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Publish this button's rectangle, so the root can draw a menu under it.
 *
 * `boundsInRoot`, not `positionInWindow`: the menu is drawn inside the same root
 * `Box`, and a window coordinate would be off by whatever inset the app is drawn
 * within.
 */
@Composable
fun rememberMenuAnchor(): Pair<Modifier, (List<MenuAction>) -> Unit> {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }
    return modifier to { items: List<MenuAction> -> MenuHost.show(bounds, items) }
}

/**
 * A row's height, rather than padding around a label.
 *
 * The label is then centred in a known box and the gaps above and below it are
 * the same by construction. Padding plus a line of text is two numbers and a
 * font's opinion, and the font wins.
 */
private val ROW_HEIGHT = 40.dp

/**
 * The pane's own top and bottom, chosen so the gaps read as one rhythm.
 *
 * Measured on the first version, which had 48dp rows and no padding here: the
 * gap between two labels came out ~37dp against ~19dp at the edges, and it was
 * reported as the items sitting far apart with uneven padding. The arithmetic is
 * forced — a label centred in a row leaves half a row above it and half below,
 * so *between* two labels is a whole row's leftover while at an edge it is half
 * of one. Making them agree means the pane carries the other half, which is
 * this.
 */
private val EDGE_PADDING = 10.dp
private val MAX_WIDTH = 280.dp

/**
 * Close the menu on the first touch outside it, without eating that touch.
 *
 * **On the root `Box`, and it has to be there.** The first version was a
 * full-screen sibling drawn under the pane, which is what a popup gives for
 * free — and it turned every gesture into two: one to close the menu and another
 * to do the thing. Reported as the menu blocking the scroll, and measured: with
 * a menu open, a drag over the feed dismissed it and the feed did not move.
 *
 * Not consuming the event is not enough, which is the part worth writing down.
 * Compose hit-tests siblings in reverse draw order and **stops at the first one
 * hit** — a node covering the screen takes the gesture from everything beneath
 * it whether or not it consumes anything. An *ancestor* is different: the
 * Initial pass runs parent to child, so the root sees the press first, decides,
 * and the list underneath still receives it. One is a lid; the other is a
 * doorbell.
 *
 * The pane's own rectangle is excluded, or a press on a row would dismiss the
 * menu before that row had its click.
 */
fun Modifier.dismissMenuOnOutsidePress(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Press || !MenuHost.open) continue
            if (!MenuHost.pane.contains(event.changes.first().position)) MenuHost.dismiss()
        }
    }
}