package com.mytube.app.domain

import com.mytube.app.domain.repository.PlaybackState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the picture has stopped because something broke, as opposed to an error
 * the player has already climbed back out of.
 */
class PlaybackFailedTest {

    @Test
    fun anErrorWithNothingMovingIsAFailure() {
        assertTrue(PlaybackState(error = "ERROR_CODE_DECODING_FAILED").failed)
    }

    @Test
    fun anErrorBeingRecoveredFromIsNotYet() {
        // Media3 re-prepares after an IO error, and iOS reattaches a stalled
        // stream: while that buffers, saying "could not play" would be wrong.
        assertFalse(PlaybackState(error = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", isBuffering = true).failed)
        assertFalse(PlaybackState(error = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", isPlaying = true).failed)
    }

    @Test
    fun nothingWrongIsNotAFailure() {
        assertFalse(PlaybackState().failed)
    }
}
