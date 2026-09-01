package com.mytube.app.ui.shell

import androidx.compose.runtime.mutableStateMapOf

/**
 * What crosses between Compose and a platform layer that draws glass over it.
 *
 * The second seam of this kind, and deliberately the same shape as
 * [ShellBridge]: one object, callbacks outward, one call inward. That one is the
 * tab bar; this one is every pane of glass over the picture.
 *
 * # Why a registry rather than a parameter
 *
 * The panes are composed in several places — the top cluster, the transport
 * discs, the clock, the fullscreen button — and they appear and disappear with
 * the controls' own fade. A parameter would mean one composable knowing about
 * all of them, which is exactly the arrangement the watch screen does not have.
 * So each [GlassPane] publishes itself here, and [App] pushes the collection
 * whenever it changes.
 *
 * # Nothing here runs on Android
 *
 * [LocalNativeGlass] is false everywhere but iOS 26, and a `GlassPane` reading
 * false paints [glassSurface] and never touches this object. `MainActivity`
 * cannot reach it even by accident.
 */
object NativeGlassBridge {

    /**
     * Every pane currently on screen, pushed whenever one moves, changes or
     * leaves.
     *
     * The whole collection rather than a diff: there are a handful of panes and
     * a list that replaces the previous one cannot drift out of step with it,
     * while an add/remove protocol can and then has to be debugged across two
     * languages.
     */
    var onPanes: ((List<NativeGlassPane>) -> Unit)? = null

    /**
     * Swift presses a control.
     *
     * The id is the [GlassItem] id, and it means the same thing on both sides
     * because Kotlin chose it. Routed through the registry rather than through a
     * handler this object holds: the pane that drew the control is the one that
     * knows what pressing it means, and it registers that alongside its
     * rectangle. An id with nothing behind it does nothing — which is the state
     * during the frame after a pane has left and before Swift has been told.
     */
    fun pressItem(id: String) {
        NativeGlassRegistry.presses[id]?.invoke()
    }
}

/**
 * A pane's rectangle, in **points**, in the hosting view's coordinates.
 *
 * Points rather than pixels because that is what SwiftUI lays out in; the
 * division by density happens in [GlassPane], where the density is known, rather
 * than on the far side where it would be one more thing to agree about.
 */
data class NativeGlassPane(
    val id: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    /** A radius in points, or **-1 for a capsule**. See `GlassPaneShape`. */
    val cornerRadius: Double,
    /**
     * Whether to draw it, which is not the same as whether Kotlin still holds
     * it.
     *
     * Compose fades its controls out and only then drops the composable. A pane
     * that waited for that disposal stayed solid for the whole fade and then
     * vanished — measured on the phone as the seek bar leaving before the
     * buttons did. So the flag turns false when the fade starts, and the two
     * sides run the same duration.
     */
    val visible: Boolean,
    val items: List<NativeGlassItem>,
)

/**
 * One thing inside a pane, in the same coordinates.
 *
 * A glyph, a word, or a dot — the clock pill and the fullscreen title carry text
 * rather than symbols, and the live badge carries a coloured circle. One type
 * with three shapes rather than three types, because what crosses a language
 * boundary is a list and a list of one kind is one loop on the far side.
 */
data class NativeGlassItem(
    val id: String,
    /** An SF Symbol name, or empty when this item is [text] or a [dot]. */
    val symbol: String,
    /** The words, when there is no symbol. */
    val text: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    /** Marked as on — drawn with an underline, the way the CC button says so. */
    val on: Boolean,
    val pointSize: Double,
    val bold: Boolean,
    /**
     * How solid it is: 0.35 for a transport control with nowhere to go, 0.7 for
     * the channel line under a fullscreen title.
     *
     * Faint rather than absent, which is a decision the charter records: a
     * removed button lets the play button slide under a thumb already reaching
     * for it.
     */
    val opacity: Double,
    /** False for a readout — the clock and the fullscreen title take no press. */
    val interactive: Boolean,
    /** A filled circle instead of a glyph: the live badge, and nothing else. */
    val dot: Boolean,
    /**
     * The colour, as `0xAARRGGBB`, or 0 for white.
     *
     * Sent rather than named so the brand red exists once, in `Tokens`. A Swift
     * copy of it is a second place it can be wrong.
     */
    val tintArgb: Long,
)

/**
 * Where the panes live between being composed and being pushed.
 *
 * A map keyed by the pane's id, so a pane that recomposes replaces itself rather
 * than appearing twice. It is snapshot state, which is what lets [App] watch it
 * with an ordinary effect instead of an observer this file would have to invent.
 */
internal object NativeGlassRegistry {
    val panes = mutableStateMapOf<String, NativeGlassPane>()

    /**
     * What each item does, kept beside where it is drawn.
     *
     * An ordinary map, not snapshot state: nothing recomposes when a callback is
     * replaced, and making it observable would recompose the whole shell every
     * time a lambda is re-created — which is every recomposition of the player.
     */
    val presses = mutableMapOf<String, () -> Unit>()

    fun put(pane: NativeGlassPane) {
        panes[pane.id] = pane
    }

    fun remove(id: String) {
        panes.remove(id)
    }
}
