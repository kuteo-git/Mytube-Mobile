package com.mytube.app.domain.model

/**
 * One of the member's collections.
 *
 * ## The default playlist is deliberately not one of these
 *
 * The saved shelf — the pinned set — is drawn as the first row wherever
 * playlists are listed, and it is *not* a `Playlist`. It is not a row in the
 * server's `playlists` table either, and that is exactly why it cannot be
 * renamed or deleted: the restriction is a fact about where it lives, rather
 * than a rule invented for the UI and forgotten at the next call site.
 *
 * Modelling it as a `Playlist` with a made-up id would put a row in the list
 * that no server call can reach, and every place that touches one would have to
 * remember which id was magic.
 *
 * Nothing here is nullable and nothing is absent: `description` is empty rather
 * than null, per §3 of the charter.
 */
data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val itemCount: Int,
    /**
     * Whether the video this list was asked about is already in it.
     *
     * Meaningful only when the question named one — the sheet asks
     * `playlists(videoId)` and gets both halves of its answer in one call.
     * Everywhere else it is false and says nothing.
     */
    val containsVideo: Boolean,
    /**
     * The first few thumbnails, for the card on the playlists screen.
     *
     * Sent with the list rather than fetched per playlist: a page of twelve
     * collections would otherwise be thirteen requests for four pictures each.
     */
    val thumbnailPaths: List<String>,
) {
    val hasThumbnail: Boolean get() = thumbnailPaths.isNotEmpty()
}
