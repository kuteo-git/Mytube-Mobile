package com.mytube.app.domain.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the iOS player is still waiting for its picture — the spinner.
 *
 * Reported on the iPhone as on the television: a spinner while the stream
 * resolved, then black until the picture came. iOS never said it was
 * buffering at all, and AVPlayer's own word is not enough: recorded on the
 * simulator, a video opened from up next showed the pause icon — AVPlayer
 * saying Playing — over black for 2.5s before the first frame.
 */
class BufferingTest {

    @Test
    fun `a player waiting to play is buffering`() {
        assertTrue(waiting(waitingToPlay = true, playedSinceLoad = false))
    }

    /** The measured case: AVPlayer says Playing, and the clock has not moved. */
    @Test
    fun `a player that says it is playing before its clock has moved is still buffering`() {
        assertTrue(waiting(waitingToPlay = false, playedSinceLoad = false))
    }

    @Test
    fun `a player whose clock has moved is playing`() {
        assertFalse(waiting(waitingToPlay = false, playedSinceLoad = true))
    }

    /** A stall in the middle of a video is buffering too. */
    @Test
    fun `a player that played and is waiting again is buffering`() {
        assertTrue(waiting(waitingToPlay = true, playedSinceLoad = true))
    }

    /** Opened paused, or paused by the viewer: nothing is being waited for. */
    @Test
    fun `a player nobody asked to play is not buffering`() {
        assertFalse(waiting(wantsToPlay = false, waitingToPlay = false, playedSinceLoad = false))
    }

    /**
     * A failed item can leave AVPlayer waiting for ever, and `failed` is
     * withheld while buffering — so "could not play" would never be shown,
     * only a spinner.
     */
    @Test
    fun `a failed item is not buffering, however long it waits`() {
        assertFalse(waiting(waitingToPlay = true, playedSinceLoad = false, itemFailed = true))
    }

    private fun waiting(
        wantsToPlay: Boolean = true,
        waitingToPlay: Boolean,
        playedSinceLoad: Boolean,
        itemFailed: Boolean = false,
    ) = isWaitingForBytes(
        wantsToPlay = wantsToPlay,
        waitingToPlay = waitingToPlay,
        playedSinceLoad = playedSinceLoad,
        itemFailed = itemFailed,
    )
}
