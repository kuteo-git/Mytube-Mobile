package com.mytube.app.domain.player

/**
 * How long the playhead may stand still before the stream is called dead.
 *
 * Six seconds, and it is a compromise between two measured facts: AVPlayer's
 * own retry rides out a ten-second outage without help, so anything shorter
 * fights a recovery that was already working, and a broadcast that has been
 * still for six seconds is not buffering — a live playlist declares
 * five-second segments.
 */
const val STALL_SECONDS = 6.0

/**
 * How often a reattach may be attempted.
 *
 * A server that is down answers a reattach as fast as it answers anything, so
 * without this the watchdog is a request every couple of seconds at a router
 * that is off.
 */
const val RECOVERY_INTERVAL_SECONDS = 5.0

/**
 * Whether a player whose picture has stopped should be started again.
 *
 * A named function rather than a chain of early returns inside the platform
 * class, for the reason `wholeSeconds` and `levelsFor` are: the decision is
 * pure, every one of its answers is a judgement somebody has to be able to
 * check, and nothing in the type system catches one of them being wrong — the
 * player reattaches either way and the fault is a video behaving oddly a
 * minute later.
 *
 * The bookkeeping stays with the platform: only it knows when the playhead
 * last moved, and *this* only knows how long ago that was.
 *
 * @param wantsToPlay somebody asked for sound and has not taken it back. The
 *   difference between "the picture is not moving because it broke" and
 *   "because somebody pressed pause", and reattaching over the second would be
 *   the app restarting a video the viewer had stopped.
 * @param hasEnded the playhead has reached the end of a recorded video. **This
 *   is a third case and it was missing.** A video that has run out is not
 *   stalled, and treating it as one is what made a finished video jump back a
 *   few seconds and play its tail again, over and over: measured on the
 *   simulator, `recover failed=false stalledFor=7.99 pos=1526.67` and then the
 *   same line again six seconds later, with the seek bar falling 1169px to
 *   1165 and climbing back each time. The jump is a few seconds rather than
 *   none because an HLS seek lands on the start of the segment it is given.
 *   A broadcast declares no duration, so it never ends and this never stops
 *   the recovery the watchdog exists for.
 * @param itemFailed the item itself reported failure, which needs no wait: it
 *   is not going to start again on its own.
 */
fun shouldRecoverStall(
    wantsToPlay: Boolean,
    hasEnded: Boolean,
    itemFailed: Boolean,
    stalledForSeconds: Double,
    sinceLastRecoverySeconds: Double,
): Boolean {
    if (!wantsToPlay) return false
    if (hasEnded) return false
    if (!itemFailed && stalledForSeconds < STALL_SECONDS) return false
    return sinceLastRecoverySeconds >= RECOVERY_INTERVAL_SECONDS
}
