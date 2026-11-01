package com.mytube.app.domain.model

/**
 * A video on YouTube that this library may not hold yet.
 *
 * Its own type rather than a [Video] with empty fields, because the two are not
 * the same thing and the difference is visible in every field the wire does not
 * carry: there is no channel *id*, no avatar, no published date, and no user
 * state at all. Squeezing it into `Video` would mean inventing a `Channel` with
 * an empty avatar path — and an empty avatar path is a request per row for a
 * 404, which is the fault the comment list already had to learn once.
 *
 * `thumbnailUrl` is absolute, and that is the second reason: everything else in
 * this app carries a path *inside* the library and is resolved against the
 * server's address. This one points at YouTube.
 */
data class ExternalVideo(
    val id: String,
    val title: String,
    /** The name alone. Upstream sends no id for it, so there is nowhere to go. */
    val channelName: String,
    val durationSeconds: Int,
    val viewCount: Long,
    /** Absolute, and pointing at YouTube rather than at the library. */
    val thumbnailUrl: String,
    /**
     * What opening this asks the server to fetch.
     *
     * The address, not the id: the gateway's `POST /api/videos/external` takes a
     * URL, and building one here from an id would be this app guessing at a
     * format the server already sent.
     */
    val sourceUrl: String,
    /**
     * Whether the catalogue already has a row for it.
     *
     * Not what decides whether it is drawn — the search screen hides an upstream
     * result whose id is already in the library half, which is a question about
     * *this screen*, not about the disk. It is what lets opening skip a round
     * trip it does not need.
     */
    val inLibrary: Boolean,
)
