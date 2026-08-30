package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.theme.Tokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * What sits behind the floating top bar, tab bar and chip row.
 *
 * All three are drawn *over* the scrolling content — that is what the web app
 * does and what makes the feed read as one continuous page rather than a strip
 * of chrome above a window. Painted solid they were a wall: content slid under
 * an opaque rectangle and stopped existing at its edge.
 *
 * # A real blur, after two attempts that were not
 *
 * The blur comes from **Haze** (`dev.chrisbanes.haze`), which is a dependency
 * this project would rather not have and earns it: nothing in Compose
 * Multiplatform 1.11 can do a backdrop blur, and both routes that look like they
 * should were built here and failed, differently:
 *
 * 1. **`UIVisualEffectView` through `UIKitView`** — the correct native answer,
 *    and it renders a flat grey band. Compose draws the scene into its own layer
 *    and gives an interop view a hole punched in it, so the effect view samples
 *    what is behind it *in the UIKit hierarchy*: the window's background. There
 *    is nothing there. This is not a tuning problem; the pixels it would need to
 *    read are not in the hierarchy it can see.
 *
 * 2. **`GraphicsLayer` with a `BlurEffect`** — record the content into a layer,
 *    record a second layer that draws the first, put the blur on the second, and
 *    draw it clipped under each bar. It compiles, it is the documented way to
 *    reuse a drawing, and it **segfaults**: `SkRecordCanvas::onDrawTextBlob`
 *    inside `PictureRecorder.finishRecordingAsPicture`, on the first frame. Skia
 *    will not take a layer being recorded while it is also being drawn in the
 *    same frame. Crash report: `Mytube-2026-08-30-235643.ips`.
 *
 * Haze solves the second problem properly — it manages the layer recording and
 * the draw order itself, rather than re-recording a layer mid-frame the way the
 * hand-rolled attempt did. **Do not replace it with a hand-rolled GraphicsLayer
 * again without a Compose release note saying that is supported**: it costs a
 * build, a device install and a crash to find out, and this comment exists so
 * that cost is paid once.
 *
 * # The wash is still here, and is not dead code
 *
 * [LocalHaze] is null wherever nothing has set up a source — a Preview, the
 * setup form, any screen outside the shell — and the gradient is what is drawn
 * there. A bar that refused to draw without a blur would be a blank rectangle
 * over the app. The wash is nearly opaque at the screen's edge, where the clock
 * and the tab labels have to stay legible, and thins toward the inside so a
 * thumbnail visibly passes *under* the bar rather than being clipped by it.
 *
 * # It has no size of its own — and that is load-bearing
 *
 * Pass `Modifier.matchParentSize()` from the Box that holds it. An earlier
 * version applied `fillMaxSize()` inside, which is a different thing: a Box
 * measures its children against the incoming constraints, so each bar grew to
 * full height — the tab row drew across the top of the screen over the clock and
 * the rest of the app went blank behind an opaque sheet.
 */
@Composable
fun BarBackdrop(
    modifier: Modifier = Modifier,
    /**
     * Whether the solid edge is the top one.
     *
     * True for the top bar and the chip row under it, false for the tab bar. The
     * gradient runs from the screen's edge inward, so getting this wrong puts
     * the thin end under the text and the solid end against nothing.
     */
    fromTop: Boolean = true,
) {
    // 0.94 against 0.72. Wide enough for the movement under it to be visible,
    // and the solid end is where every glyph sits — the tab labels are 10sp, and
    // 10sp of grey over a passing thumbnail is unreadable much below 0.9.
    val haze = LocalHaze.current
    if (haze != null) {
        Box(
            modifier.hazeEffect(state = haze) {
                // 24dp: enough that a headline passing underneath is texture
                // rather than words — a backdrop that can still be read is a
                // distraction, not a background.
                blurRadius = 24.dp
                // A tint over the blur, as every system that does this applies
                // one. The blur says the content continues underneath; the tint
                // is what keeps a 10sp tab label legible over whatever happens
                // to be passing. Lighter than the wash below, because the blur
                // is already doing most of that work.
                tints = listOf(HazeTint(Tokens.bg.copy(alpha = 0.55f)))
            },
        )
        return
    }

    val edge = Tokens.bg.copy(alpha = 0.94f)
    val inner = Tokens.bg.copy(alpha = 0.72f)
    val stops = if (fromTop) listOf(edge, inner) else listOf(inner, edge)

    Box(modifier.background(Brush.verticalGradient(stops)))
}

/**
 * What the bars blur, or null where nothing publishes one.
 *
 * A composition local rather than a parameter for the reason
 * [LocalMiniPlayerShowing] is one: it is an ambient fact about the shell, three
 * different places read it, and threading it through every screen signature is
 * how the next one to pin something forgets.
 */
val LocalHaze = compositionLocalOf<HazeState?> { null }
