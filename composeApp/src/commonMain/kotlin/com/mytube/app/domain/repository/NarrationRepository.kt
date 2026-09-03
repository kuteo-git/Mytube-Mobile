package com.mytube.app.domain.repository

import com.mytube.app.domain.model.Narration

/**
 * Narration for one video, as the server prepares it.
 *
 * Its own port rather than more methods on `VideoRepository`. That one answers
 * questions about the catalogue; this one drives a job — it starts work, and it
 * is polled while the work runs. Two different kinds of thing, and a screen that
 * needs only the first should not be handed the second.
 */
interface NarrationRepository {

    /** Begin, or continue, a pass. Returns as soon as the server has accepted. */
    /**
     * Begin a pass, from where the viewer is.
     *
     * [fromSeconds] is not a filter and nothing is skipped: the server speaks
     * from that moment to the end and then goes back for the beginning. A pass
     * takes minutes, and somebody who has seeked to 1:20 is waiting for 1:20 —
     * starting at zero spends all of it on lines already gone past.
     *
     * No default. A caller that does not pass one is a caller that has silently
     * asked for the beginning, and that is exactly the fault this replaces —
     * the same reason `startAtBeginning` lost its own default.
     */
    suspend fun start(videoId: String, fromSeconds: Double)

    /** What is ready so far. */
    suspend fun state(videoId: String): Narration
}
