package com.mytube.app.domain.model

/**
 * One playlist a channel published, as the channel page's Playlists tab draws
 * it.
 *
 * Nothing to do with [Playlist], the household's own collections: this is a list
 * a YouTube channel made, read from upstream and never written. It carries only
 * what a card needs and the id to open it with — the videos arrive when it is
 * opened, not before.
 *
 * Nothing is nullable and nothing is absent, per charter §3: a count the server
 * did not state is zero, read through [hasCount] rather than tested as a number
 * at each call site.
 */
data class ChannelPlaylist(
    val id: String,
    val title: String,
    /** Absolute, the way an upstream thumbnail is; `imageModel` notices. */
    val thumbnailUrl: String,
    val videoCount: Int,
) {
    val hasCount: Boolean get() = videoCount > 0
    val hasThumbnail: Boolean get() = thumbnailUrl.isNotEmpty()
}
