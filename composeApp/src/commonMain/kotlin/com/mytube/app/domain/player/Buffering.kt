package com.mytube.app.domain.player

/**
 * Whether the iOS player is still waiting for its picture — the spinner.
 *
 * AVPlayer's `WaitingToPlayAtSpecifiedRate` is the iOS half of Media3's
 * BUFFERING, but on its own it is not enough: recorded on the simulator, a
 * video opened from up next was reported as Playing over a black frame for
 * 2.5s. So until the playhead has actually moved since the item was loaded or
 * sought, a player that was asked to play is still waiting.
 *
 * A failed item is never waiting, however AVPlayer describes it:
 * `PlaybackState.failed` is withheld while buffering, and answering yes there
 * would trade "could not play" for a spinner that never ends. A named function
 * for the reason [shouldRecoverStall] is one: the platform class cannot be
 * tested, and this is the judgement in it.
 *
 * @param wantsToPlay somebody asked for sound and has not taken it back — a
 *   video opened paused is not waiting for anything.
 * @param waitingToPlay AVPlayer says it is waiting for data.
 * @param playedSinceLoad the playhead has advanced while playing since the last
 *   load, seek or reattach.
 */
fun isWaitingForBytes(
    wantsToPlay: Boolean,
    waitingToPlay: Boolean,
    playedSinceLoad: Boolean,
    itemFailed: Boolean,
): Boolean = wantsToPlay && !itemFailed && (waitingToPlay || !playedSinceLoad)
