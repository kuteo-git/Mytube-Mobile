package com.mytube.app.ui.tv

/**
 * Where a run of left/right presses on the remote is heading, before it is sent.
 *
 * # Why a draft and not a seek per press
 *
 * A D-pad held down repeats a key about twenty times a second. Seeking the player
 * on each would ask the server for twenty different pieces of a video in a
 * second, and the picture would stutter through every one of them. So the
 * presses move a *target*, the storyboard shows where it is, and the player is
 * sent there once the remote has gone quiet — which is what YouTube's TV app
 * does, and why its bar runs ahead of the picture while a key is held.
 *
 * [presses] counts the run, which is what makes holding the key go faster the
 * longer it is held: see [tvSeekStep].
 */
data class SeekDraft(
    val active: Boolean,
    val targetSeconds: Double,
    val presses: Int,
) {
    companion object {
        val None = SeekDraft(active = false, targetSeconds = 0.0, presses = 0)
    }
}

/**
 * How far one press moves the target, given how many came before it in this run.
 *
 * Ten seconds for a tap or a few, which is the phone's double-tap and the web's
 * arrow key. Held, it climbs — to thirty, then a minute — because a remote has
 * no finger to drag across the bar, and crossing a two-hour film ten seconds at
 * a time is seven hundred presses.
 */
internal fun tvSeekStep(presses: Int): Double = when {
    presses < FAST_AFTER -> 10.0
    presses < FASTER_AFTER -> 30.0
    else -> 60.0
}

/**
 * The target after one more press, kept inside the video.
 *
 * Never past the last second: seeking to the very end of a stream ends it, and
 * autoplay would take that as the video having been watched to the end. A
 * duration of zero is "not known yet", and is no ceiling at all rather than a
 * ceiling of zero.
 */
internal fun tvSeekTarget(fromSeconds: Double, bySeconds: Double, durationSeconds: Double): Double {
    val moved = (fromSeconds + bySeconds).coerceAtLeast(0.0)
    if (durationSeconds <= 0.0) return moved
    return moved.coerceAtMost((durationSeconds - 1.0).coerceAtLeast(0.0))
}

/**
 * The draft after one more press in [direction] (−1 back, +1 forward).
 *
 * A new run starts from the playhead; a run already going starts from its own
 * target, so the presses add up rather than each one measuring from a playhead
 * that has not moved yet.
 */
internal fun SeekDraft.press(direction: Int, positionSeconds: Double, durationSeconds: Double): SeekDraft {
    val from = if (active) targetSeconds else positionSeconds
    val count = if (active) presses else 0
    return SeekDraft(
        active = true,
        targetSeconds = tvSeekTarget(from, direction * tvSeekStep(count), durationSeconds),
        presses = count + 1,
    )
}

/** Presses in a run before a step becomes thirty seconds: a tap or a short hold. */
private const val FAST_AFTER = 6

/** …and before it becomes a minute: about a second of holding. */
private const val FASTER_AFTER = 20

/**
 * How long the remote has to be quiet before the draft is sent to the player.
 *
 * Long enough that a key held down, which repeats every 50ms or so, never
 * commits halfway; short enough that one tap is answered before anybody
 * wonders whether it was received.
 */
const val SEEK_COMMIT_MILLIS = 700L
