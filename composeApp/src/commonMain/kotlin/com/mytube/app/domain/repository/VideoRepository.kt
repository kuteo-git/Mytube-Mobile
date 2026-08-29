package com.mytube.app.domain.repository

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Topic
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

    /** The categories with videos in them, most populated first. */
    suspend fun topics(): List<Topic>

    /**
     * What is on air, for this member.
     *
     * Its own call rather than a topic: the server filters it to the viewer's
     * own subscriptions and returns everything at once with no page token,
     * because *"everything on air" is the whole promise* and the set is a few
     * dozen.
     */
    suspend fun live(): List<Video>

    /**
     * Recently watched, newest first.
     *
     * The feed's "continue watching" rail is built from this rather than from a
     * slot in the feed, which is what the web app does — and it does it that way
     * because the two lists answer different questions and editing one to keep
     * the other honest is how a card stays on screen after being marked watched.
     */
    suspend fun history(limit: Int = 24): List<Video>

    /**
     * The channels this member follows, by name.
     *
     * On the port that answers about videos rather than one of its own, because
     * a repository is a boundary and not a table: splitting it would mean a
     * second class with the same address, the same header rule and the same
     * error handling, differing only in which two endpoints it reaches.
     */
    suspend fun subscriptions(): List<Channel>

    /** What to play after this one. */
    suspend fun upNext(videoId: String): List<Video>

    /**
     * Where the viewer has got to.
     *
     * Returns nothing and must not be waited on: what it feeds — Continue
     * watching, and the ranker's WATCH signal — tolerates a missed report far
     * better than a viewer tolerates a stutter.
     */
    suspend fun recordProgress(videoId: String, positionSeconds: Double, watchedFraction: Double)

    suspend fun setReaction(videoId: String, reaction: Reaction)

    /** Keep this file against the eviction sweep, or stop keeping it. */
    suspend fun setSaved(videoId: String, saved: Boolean)

    suspend fun setSubscribed(channelId: String, subscribed: Boolean)

    /**
     * A channel and a page of its uploads.
     *
     * `sortToken` is opaque and comes from a previous page's options; empty asks
     * for whatever order the server gives by default.
     */
    suspend fun channelPage(
        channelId: String,
        sortToken: String = "",
        pageToken: String = "",
    ): ChannelPage
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

/**
 * A channel, and a page of what it has published.
 *
 * One object rather than two calls returning separately, because the videos
 * cannot be drawn without the channel: the endpoint sends no channel per row,
 * and every card needs a name and an avatar.
 */
data class ChannelPage(
    val channel: Channel,
    val videoCount: Int,
    val videos: List<Video>,
    val sortOptions: List<SortOption>,
    val nextPageToken: String,
) {
    val hasMore: Boolean get() = nextPageToken.isNotEmpty()
}

/**
 * One of the orders a channel's uploads can be asked for.
 *
 * The label comes from YouTube and is **not translated** — it arrives as
 * "Latest", "Popular", "Oldest" in whatever language upstream answered in, and
 * the token beside it is opaque. Translating a label whose value this app did
 * not choose would mean guessing which of three it was, and being wrong on the
 * day upstream adds a fourth.
 */
data class SortOption(val label: String, val token: String)
