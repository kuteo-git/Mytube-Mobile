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
    suspend fun start(videoId: String)

    /** What is ready so far. */
    suspend fun state(videoId: String): Narration
}
