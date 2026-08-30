package com.mytube.app.ui.watch

/**
 * Dragging the watch screen down into the miniplayer.
 *
 * ## Why this is the web app's arithmetic rather than its own
 *
 * The first version translated the whole screen downwards and dropped it past a
 * third of the height. It worked and it did not match: on the web the *picture*
 * travels, shrinking as it goes until it is the thumbnail in the bar, while
 * everything under it fades away. Two apps doing one gesture two ways is two
 * things to learn, so the numbers here are read across from
 * `web/src/features/watch/application/player-gesture.ts` rather than guessed.
 *
 * ## What travels, and against what
 *
 * Progress is measured against the distance the picture actually has to cover —
 * the gap between where it sits full size and where the bar is — and **not**
 * against the picture's own height. That was the web's own first mistake and it
 * was plainly visible: a phone player is about 220px tall while the bar is some
 * 640px away, so progress reached 1 after a third of the movement and the
 * picture arrived while the finger was still mid-screen. It read as the player
 * fleeing the hand dragging it.
 */

/** Movement before a drag is admitted. A finger never lands perfectly still. */
const val DRAG_SLOP = 12f

/**
 * How far down counts as "put it away", as a fraction of the *picture's*
 * height — not of the journey.
 *
 * A quarter of the picture is a movement the hand can feel; a quarter of the
 * way to the bar is most of the screen and far too much to ask.
 */
const val COMMIT_FRACTION = 0.25f

/**
 * Downward speed that commits regardless of distance, in pixels per second.
 *
 * A flick is an unambiguous statement and should not have to travel far to be
 * heard; without this a fast short flick springs back, which reads as the
 * gesture having been missed rather than declined.
 *
 * The version before this had no velocity term at all, reasoning that reading
 * speed lets a flick meant as a scroll throw the video away. That risk is real
 * and it is answered by where the gesture may start — on the picture, which
 * does not scroll — rather than by refusing to look at speed.
 */
const val COMMIT_VELOCITY = 700f

/** How much of the finger's travel the picture follows. One to one. */
fun dragOffset(totalDrag: Float): Float = totalDrag.coerceAtLeast(0f)

/**
 * How far along its journey the picture is, 0 to 1.
 *
 * `travel` is the distance to the bar, so at any moment the picture sits at
 * exactly `start + dy`: it stays under the finger because the arithmetic says
 * so, not because a constant was tuned until it looked about right.
 */
fun travelProgress(dy: Float, travel: Float): Float =
    if (travel <= 0f) 0f else (dy / travel).coerceIn(0f, 1f)

/** Whether letting go here should put the video away. */
fun shouldCommit(dy: Float, playerHeight: Float, velocity: Float): Boolean {
    if (velocity >= COMMIT_VELOCITY) return true
    if (playerHeight <= 0f) return false
    return dy >= playerHeight * COMMIT_FRACTION
}

/** Pixels per second, from a movement and the time it took. */
fun velocityOf(dy: Float, elapsedMillis: Long): Float =
    if (elapsedMillis <= 0L) 0f else (dy / elapsedMillis) * 1000f

/** Linear, deliberately: an eased value runs ahead of the finger. */
fun lerp(from: Float, to: Float, t: Float): Float =
    from + (to - from) * t.coerceIn(0f, 1f)
