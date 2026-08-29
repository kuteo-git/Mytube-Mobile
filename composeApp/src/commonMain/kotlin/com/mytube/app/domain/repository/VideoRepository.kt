package com.mytube.app.domain.repository

import com.mytube.app.domain.model.Video

/**
 * What the app can ask about videos.
 *
 * ## Why the interface lives here and not beside its implementation
 *
 * This is the port, and it points inward. `domain` declares what it needs; `data`
 * supplies it. That direction is the whole of what makes the layering worth
 * having — a use case written against this interface does not know whether the
 * answer came from the gateway, from a cache, or from a fake in a test, and none
 * of those can change it.
 *
 * Put this file in `data/` instead and the arrow reverses: the domain would then
 * depend on the thing that knows about HTTP, and there would be no point in
 * having two folders. `ArchitectureGuardTest` is what stops that happening by
 * accident.
 *
 * ## Why it returns domain types and not DTOs
 *
 * The gateway's JSON carries fields no screen here reads — storage bookkeeping,
 * ranking provenance, eviction timestamps. Mapping at the edge means a field the
 * server renames breaks one mapper rather than every caller.
 *
 * ## Why suspend and not Flow
 *
 * These are one-shot questions with one answer. A `Flow` promises a stream of
 * updates that nothing here produces: the server pushes nothing, and what looks
 * like a live value — a download landing, captions arriving — is polling, which
 * belongs to whoever is doing the polling rather than to the shape of every
 * repository method.
 */
interface VideoRepository {

    /**
     * A page of the home feed.
     *
     * `pageToken` is opaque and comes from the previous page; empty asks for the
     * first. The server decides whether it means an offset or a cursor, and this
     * side must not care —
     * §6 of the server charter freezes a ranking into a per-session snapshot,
     * and a client that reconstructed the token would be reconstructing a
     * decision it cannot see.
     */
    suspend fun feed(topic: String = "", pageToken: String = ""): FeedPage

    suspend fun video(id: String): Video

    /** Videos matching a query, from this library and from YouTube. */
    suspend fun search(query: String): List<Video>
}

data class FeedPage(
    val videos: List<Video>,
    /**
     * The token for the next page, or empty when this is the last one.
     *
     * Empty rather than null: see Video.publishedAt for why nothing in `domain`
     * is nullable. Ask `hasMore` rather than testing the string.
     */
    val nextPageToken: String,
) {
    val hasMore: Boolean get() = nextPageToken.isNotEmpty()
}
