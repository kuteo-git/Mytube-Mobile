package com.mytube.app.domain.repository

import com.mytube.app.domain.model.Stream

/**
 * How to play one video.
 *
 * Separate from [VideoRepository] because the two answer different questions
 * about different things. A video's title and channel come from the catalogue
 * and change rarely; how it can be played is asked at the moment somebody presses
 * play, may involve talking to YouTube, and can answer differently a minute
 * later. Putting both behind one interface would suggest they are alike.
 */
interface StreamRepository {

    /**
     * @param maxHeight the ceiling this device should be offered, which the
     *   server applies when it writes the playlist. See the charter: on iOS a cap
     *   cannot be applied in the client at all.
     */
    suspend fun stream(videoId: String, maxHeight: Int): Stream
}
