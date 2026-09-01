package com.mytube.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A pane of glass that the **platform** draws, positioned by Compose.
 *
 * # Why this seam exists at all
 *
 * [glassSurface] is paint: a dark fill, a diagonal sheen and a rim. It was
 * written because a real backdrop blur is impossible over the picture —
 * [BarBackdrop] samples a layer Compose records, and the video is an
 * `AVPlayerLayer` inside a `UIKitView` on iOS and a `PlayerView` on Android,
 * neither of which Compose draws into that layer. That reasoning is still
 * correct, and it is why this file is not simply a better `glassSurface`.
 *
 * What changed is a measurement. **A SwiftUI `.glassEffect()` laid over the
 * whole Compose hosting view does sample the `AVPlayerLayer`** — measured on the
 * iPhone 16e simulator with a throwaway capsule over a playing video, two
 * screenshots 1.2 seconds apart: the content inside the capsule was blurred, and
 * it *changed* between the two. That last part is the whole measurement. One
 * screenshot cannot tell a live sample from a stale snapshot, and a stale
 * snapshot would be worse than no glass at all.
 *
 * The failure recorded in [BarBackdrop] is the opposite arrangement and still
 * fails: an effect view *inside* Compose through `UIKitView` punches a hole in
 * the scene and ends up looking at the window's background.
 *
 * # Compose owns the layout; the platform owns the paint
 *
 * The obvious way to draw a native control is to write it natively — its size,
 * its padding, where it sits. That is how two shells drift: every number
 * describing this player would exist in Kotlin for Android and again in Swift
 * for iOS, with nothing to report them disagreeing.
 *
 * So the composable is **still composed**. It measures and places exactly as it
 * did, and `drawWithContent {}` suppresses only its painting. What crosses the
 * boundary is the rectangle it landed on: Swift is told where to draw, never how
 * big a thing is.
 *
 * The consequence, and it is not a small one: **the content has to cross too.**
 * The platform layer is above everything Compose renders, so a glyph left in
 * Compose would be blurred by the very pane it belongs to. That is what
 * [GlassItem] is for.
 *
 * # What this is not for
 *
 * A pane inside a scrolling list works — it is an ordinary layout node, so a
 * `LazyColumn` and `Arrangement.spacedBy` place it like anything else — but it
 * pushes a new rectangle across the language boundary on every frame of the
 * scroll, and the platform layer is not clipped by the list it appears to be
 * inside. Everything *on the page* keeps [glassControl], which is paint and
 * costs nothing. This is for chrome that stays still over the picture, where the
 * material cannot be painted at all.
 */
@Composable
fun GlassPane(
    /** Stable across recompositions: it is the key the platform layer diffs on. */
    id: String,
    shape: GlassPaneShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!LocalNativeGlass.current) {
        Box(modifier.glassSurface(shape.compose()), content = { content() })
        return
    }

    val density = LocalDensity.current.density
    var pane by remember { mutableStateOf(EMPTY_RECT) }
    // One map per pane, filled by the [GlassItem]s composed inside it. A map
    // rather than a list because children report in whatever order they happen
    // to be measured, and each is identified by its key either way.
    val items = remember { mutableStateMapOf<String, NativeGlassItem>() }

    Box(
        modifier
            .onGloballyPositioned { layout ->
                val origin = layout.positionInRoot()
                pane = NativeGlassPane(
                    id = id,
                    x = (origin.x / density).toDouble(),
                    y = (origin.y / density).toDouble(),
                    width = (layout.size.width / density).toDouble(),
                    height = (layout.size.height / density).toDouble(),
                    cornerRadius = shape.radius,
                    visible = true,
                    items = emptyList(),
                )
            }
            // Measured and placed, never painted. The children are still here —
            // that is what keeps one layout for both platforms — they simply do
            // not reach the screen, because the platform draws them instead.
            .drawWithContent { },
    ) {
        CompositionLocalProvider(LocalGlassItems provides items) { content() }
    }

    // Sorted by x so the platform receives them in reading order rather than in
    // whatever order they were measured. Nothing depends on it today; a list
    // that reshuffles between pushes is the kind of thing that costs an
    // afternoon later.
    val contents = items.values.sortedBy { it.x }
    // Whether the platform should be drawing it *now*, which is not the same as
    // whether it is composed.
    //
    // The two came apart on a real phone and were reported: Compose fades its
    // own controls and drops the composable at the end of that fade, so a pane
    // that left the platform only on disposal was still solid while the seek bar
    // beside it had already gone. The flag is pushed at the *start* of the fade,
    // and both sides run the same duration.
    val visible = LocalGlassVisible.current
    LaunchedEffect(pane, contents, visible) {
        if (pane.width > 0.0) {
            NativeGlassRegistry.put(pane.copy(items = contents, visible = visible))
        }
    }

    // A pane whose composable has left the tree must leave the platform layer
    // with it. Without this the controls fade out and the glass stays — and the
    // fault would be invisible on Android, where none of this runs.
    DisposableEffect(id) { onDispose { NativeGlassRegistry.remove(id) } }
}

/**
 * One control inside a [GlassPane].
 *
 * `content` is what Android and every iOS before 26 draw. On iOS 26 it is
 * measured and placed and not painted, and `symbol` is drawn in its place.
 *
 * **Why an SF Symbol and not the app's own vector.** The pane is drawn by the
 * platform, above everything Compose renders — a glyph left in Compose would sit
 * *under* the glass and be blurred by it. Something has to name the picture on
 * the Swift side, and `ShellBar.swift` already settled the reasoning: a control
 * the system draws should use the system's glyphs. The cost is that these
 * buttons look slightly different on iOS 26 than on Android, which is the debt
 * the "iOS first" decision accepted.
 */
@Composable
fun GlassItem(
    id: String,
    /** An SF Symbol name, or empty for [text] or a [dot]. */
    symbol: String = "",
    /** The words, for a readout like the clock or the fullscreen title. */
    text: String = "",
    /**
     * What pressing it does, or null for a readout.
     *
     * The same lambda the `content` button carries, passed again rather than
     * reached into: on Android the Compose button calls it directly, on iOS 26
     * the platform does, and neither can see the other's copy.
     */
    onPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Drawn with an underline beneath it, the way the CC button marks its state. */
    on: Boolean = false,
    pointSize: Double = 19.0,
    bold: Boolean = false,
    opacity: Double = 1.0,
    /** A filled circle rather than a glyph — the live badge. */
    dot: Boolean = false,
    /** `0xAARRGGBB`, or 0 for white. */
    tintArgb: Long = 0L,
    content: @Composable () -> Unit,
) {
    val sink = LocalGlassItems.current
    if (sink == null) {
        Box(modifier, content = { content() })
        return
    }

    // Re-registered on every recomposition, because the lambda is usually a new
    // object each time and the stale one closes over stale state.
    if (onPress != null) NativeGlassRegistry.presses[id] = onPress

    val density = LocalDensity.current.density
    var placed by remember { mutableStateOf(EMPTY_ITEM) }

    Box(
        modifier.onGloballyPositioned { layout ->
            val origin = layout.positionInRoot()
            placed = NativeGlassItem(
                id = id,
                symbol = symbol,
                text = text,
                x = (origin.x / density).toDouble(),
                y = (origin.y / density).toDouble(),
                width = (layout.size.width / density).toDouble(),
                height = (layout.size.height / density).toDouble(),
                on = on,
                pointSize = pointSize,
                bold = bold,
                opacity = opacity,
                interactive = onPress != null,
                dot = dot,
                tintArgb = tintArgb,
            )
        },
        content = { content() },
    )

    // Position and state are published together, and every piece of state is a
    // key. A rectangle that has not moved fires no `onGloballyPositioned`, so a
    // clock whose text changed each second — and it does — would otherwise show
    // the time it had when the controls appeared.
    LaunchedEffect(placed, on, symbol, text, opacity) {
        if (placed.width > 0.0) {
            sink[id] = placed.copy(on = on, symbol = symbol, text = text, opacity = opacity)
        }
    }

    DisposableEffect(id) {
        onDispose {
            sink.remove(id)
            NativeGlassRegistry.presses.remove(id)
        }
    }
}

/**
 * The outline, in the two forms the platform can draw.
 *
 * A capsule is not "a large radius": SwiftUI's `.capsule` stays round-ended at
 * any height, while a fixed radius chosen to match at 48dp is wrong the moment a
 * pane is a different size.
 */
sealed interface GlassPaneShape {
    data object Capsule : GlassPaneShape
    data class Radius(val dp: Double) : GlassPaneShape
}

private fun GlassPaneShape.compose() = when (this) {
    is GlassPaneShape.Capsule -> RoundedCornerShape(percent = 50)
    is GlassPaneShape.Radius -> RoundedCornerShape(dp.dp)
}

private val GlassPaneShape.radius: Double
    get() = when (this) {
        // Negative rather than some huge number: the platform reads it as
        // "capsule", and a sentinel that is not a legal radius cannot be
        // mistaken for one.
        is GlassPaneShape.Capsule -> -1.0
        is GlassPaneShape.Radius -> dp
    }

/**
 * Whether the platform is drawing the glass.
 *
 * True only where Swift has a layer over the Compose scene — iOS 26. A
 * composition local rather than a parameter for the reason [LocalBackdrop] is
 * one: several controls read it, and threading it through every signature is how
 * the next one forgets.
 */
val LocalNativeGlass = compositionLocalOf { false }

/**
 * Whether the panes under this point in the tree are showing.
 *
 * A local rather than a parameter because it is the same answer for every pane
 * on the screen — the controls come and go together — and there are seven of
 * them, three inside a helper that would otherwise have to carry the flag
 * through for no reason of its own.
 *
 * It is a *drawing* fact, not a layout one: a pane told false is still measured
 * and placed, so nothing moves when the controls come back.
 */
val LocalGlassVisible = compositionLocalOf { true }

internal val LocalGlassItems =
    compositionLocalOf<SnapshotStateMap<String, NativeGlassItem>?> { null }

private val EMPTY_RECT =
    NativeGlassPane("", 0.0, 0.0, 0.0, 0.0, -1.0, true, emptyList())

private val EMPTY_ITEM = NativeGlassItem(
    id = "",
    symbol = "",
    text = "",
    x = 0.0,
    y = 0.0,
    width = 0.0,
    height = 0.0,
    on = false,
    pointSize = 0.0,
    bold = false,
    opacity = 1.0,
    interactive = false,
    dot = false,
    tintArgb = 0L,
)
