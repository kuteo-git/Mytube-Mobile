package com.mytube.app.domain.model

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The stills a scrub bar draws, and where to find each one.
 *
 * YouTube publishes previews as sprite sheets — one image carrying a grid of
 * small frames — and the server copies them under the media root and answers with
 * this geometry. Nothing here is a picture: turning a moment into a crop is
 * arithmetic, and doing it on the device is what makes scrubbing free. The
 * alternative was a route answering one still per request, which under a moving
 * thumb is a request per frame.
 *
 * Nothing is nullable, per §3. A video with no preview is [None] rather than a
 * [Storyboard] with an empty list, so a screen cannot forget to ask — most of
 * this library has no storyboard at all, and that has to be a state rather than
 * an edge case somebody remembers to check.
 */
sealed interface StoryboardState {

    /** Not asked yet, or asked and still on its way. */
    data object Loading : StoryboardState

    /**
     * This video has no preview, and that is a finished answer.
     *
     * A Short too brief for a ladder, an upload YouTube has not processed, or a
     * video upstream will not discuss. The bar keeps the time readout it had
     * before previews existed, which is why this is not an error.
     */
    data object None : StoryboardState

    data class Ready(val board: Storyboard) : StoryboardState
}

/**
 * Sheets on disk, described.
 *
 * @property tileWidth the size of one still, in **pixels** — not dp. It is a fact
 *   about an image file, and the screen decides how large to draw it.
 */
data class Storyboard(
    val tileWidth: Int,
    val tileHeight: Int,
    val rows: Int,
    val columns: Int,
    /**
     * How much of the video one still stands for.
     *
     * Taken from a sheet's own duration by the server rather than from the
     * video's, because the last sheet is usually only partly filled: dividing the
     * video's length by the total number of slots stretches every still's share
     * by however much of that sheet is blank, and the preview then drifts further
     * the longer somebody scrubs.
     */
    val intervalSeconds: Double,
    /** The sheets in order, as paths relative to the media root. */
    val sprites: List<String>,
) {
    /** False for an answer that arrived but cannot be drawn from. */
    val isDrawable: Boolean
        get() = rows > 0 && columns > 0 && intervalSeconds > 0.0 && sprites.isNotEmpty() &&
            tileWidth > 0 && tileHeight > 0

    /** How many stills one sheet holds. */
    val framesPerSheet: Int get() = rows * columns
}

/** Which sheet a still is on, and how far into it. */
data class StoryboardFrame(
    /** The sheet's path relative to the media root; still needs `imageModel`. */
    val sprite: String,
    /**
     * How far into the sheet the still sits, in **pixels**, to its top left.
     *
     * Positive. A caller draws it by shifting the sheet the other way, which on
     * this platform is a negative offset inside a clipping box. Returned as a
     * distance rather than as a modifier so the web client can do the same sum.
     */
    val x: Int,
    val y: Int,
)

/**
 * The still to draw for a moment, or `null` when there is none to draw.
 *
 * `null` rather than the first frame for a board that cannot be read: a frame
 * pointing at sheet zero would put the opening shot under every moment of the
 * video, which looks like a preview that works and is wrong everywhere but the
 * start. The one absence in this file, and it is the *answer* rather than a
 * missing field — §3's rule is about fields the wire omits.
 *
 * A named function with a test for the reason `wholeSeconds` and `levelsFor` are
 * ones: every answer here is a judgement nothing in the type system checks. Read
 * column-major instead of row-major and the preview draws a moment from five
 * stills away for the whole video, with nothing failing.
 */
fun Storyboard.frameAt(seconds: Double): StoryboardFrame? {
    if (!isDrawable || seconds.isNaN()) return null

    // Clamped at both ends, and the far end is the one that matters.
    //
    // The last sheet is usually only partly filled — six sheets of 25 hold 150
    // slots for a video with 128 stills — so a moment near the end lands in a
    // cell that exists while the one after it does not. Clamping to the last cell
    // keeps a picture on the bar all the way to its end, which is exactly where
    // somebody looking for the closing scene drags to.
    val lastFrame = sprites.size * framesPerSheet - 1
    val frame = min(max(floor(max(seconds, 0.0) / intervalSeconds).toInt(), 0), lastFrame)

    val sheet = frame / framesPerSheet
    val within = frame % framesPerSheet
    return StoryboardFrame(
        sprite = sprites[sheet],
        // Row-major, which is how a sheet is laid out: the second still is to the
        // right of the first, not under it.
        x = (within % columns) * tileWidth,
        y = (within / columns) * tileHeight,
    )
}
