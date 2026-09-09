package com.mytube.app.domain.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a player whose picture has stopped is started again.
 *
 * The watchdog exists because iOS suspends an app within seconds of its sound
 * stopping, so a broadcast that stalls overnight is the app ending — that is
 * §1 of the charter, and it is why the default answer to a stopped picture is
 * to reattach.
 *
 * It had three cases and there are four. A finished video is not a stalled
 * one, and calling it stalled is what made a video that had run out jump back
 * a few seconds and play its tail again — measured on the simulator, twice
 * within one minute, with the seek bar falling and climbing each time.
 */
class StallRecoveryTest {

    @Test
    fun `a video that has ended is not stalled`() {
        assertFalse(
            shouldRecoverStall(
                wantsToPlay = true,
                hasEnded = true,
                itemFailed = false,
                // Long past the threshold, which is the point: the playhead has
                // stopped and it is never going to move again on its own.
                stalledForSeconds = 60.0,
                sinceLastRecoverySeconds = 60.0,
            ),
        )
    }

    @Test
    fun `a stalled stream is reattached`() {
        assertTrue(
            shouldRecoverStall(
                wantsToPlay = true,
                // A broadcast declares no duration, so it never ends — this is
                // the case the watchdog was written for.
                hasEnded = false,
                itemFailed = false,
                stalledForSeconds = 7.0,
                sinceLastRecoverySeconds = 30.0,
            ),
        )
    }

    @Test
    fun `a paused video is left alone`() {
        assertFalse(
            shouldRecoverStall(
                wantsToPlay = false,
                hasEnded = false,
                itemFailed = false,
                stalledForSeconds = 600.0,
                sinceLastRecoverySeconds = 600.0,
            ),
        )
    }

    @Test
    fun `a picture that has only just stopped is given time`() {
        assertFalse(
            shouldRecoverStall(
                wantsToPlay = true,
                hasEnded = false,
                itemFailed = false,
                stalledForSeconds = 2.0,
                sinceLastRecoverySeconds = 600.0,
            ),
        )
    }

    @Test
    fun `a failed item waits for nothing`() {
        assertTrue(
            shouldRecoverStall(
                wantsToPlay = true,
                hasEnded = false,
                itemFailed = true,
                stalledForSeconds = 0.0,
                sinceLastRecoverySeconds = 600.0,
            ),
        )
    }

    @Test
    fun `a reattach is not made twice in a row`() {
        assertFalse(
            shouldRecoverStall(
                wantsToPlay = true,
                hasEnded = false,
                itemFailed = true,
                stalledForSeconds = 60.0,
                sinceLastRecoverySeconds = 1.0,
            ),
        )
    }
}
