package com.mytube.app.domain.repository

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.ExternalVideo
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

    /**
     * What the followed channels published recently and this viewer has not
     * watched.
     *
     * A page like [feed]'s, and a separate call rather than a topic on that one:
     * the feed answers "what should this household see", which is a mix with a
     * tenth of a page reserved for new uploads. This answers "did I miss
     * anything", which is a list that runs out — and running out is the point.
     *
     * The window it looks back over belongs to the server. See
     * `GatewayDataSource.missed`.
     */
    suspend fun missed(pageToken: String = ""): FeedPage

    suspend fun video(id: String): Video

    /** Videos in *this library* matching a query. */
    suspend fun search(query: String): List<Video>

    /**
     * Videos on YouTube matching a query.
     *
     * A second call rather than a second half of [search], because the two are
     * two round trips with two failure modes: the library answers off a local
     * index in milliseconds, and this one drives yt-dlp against the internet.
     * Folding them together would make a screen that cannot show the half that
     * worked.
     *
     * Upstream has **no cursor**. Asking for more means asking for a larger
     * page, and the answer is complete when it comes back shorter than [limit].
     */
    suspend fun discover(query: String, limit: Int): List<ExternalVideo>

    /**
     * Make a catalogue row for an upstream video, and answer with its id.
     *
     * Only metadata is written; the download starts when the player asks how to
     * play it, exactly as for any other video. Nothing can open an upstream
     * result without this — the watch screen loads by id, and there is no row to
     * load until somebody asks for one.
     */
    suspend fun ensureExternal(sourceUrl: String): String

    /**
     * The channel a pasted address names, or empty when the query is not one.
     *
     * Empty rather than null, and the reason is §3's: nothing inward of the
     * mapper is nullable, so "this was not an address" is a value the caller
     * checks rather than a case it can forget. The gateway is asked about every
     * query — most of them are not addresses, and it says so with a 200.
     */
    suspend fun resolveChannel(query: String): String

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

    /** Videos this member has kept against the eviction sweep. */
    suspend fun saved(): List<Video>

    suspend fun feedMix(): FeedMix

    suspend fun saveFeedMix(mix: FeedMix)

    /**
     * The channels this member follows, by name.
     *
     * On the port that answers about videos rather than one of its own, because
     * a repository is a boundary and not a table: splitting it would mean a
     * second class with the same address, the same header rule and the same
     * error handling, differing only in which two endpoints it reaches.
     */
    suspend fun subscriptions(): List<Channel>

    /**
     * A caption file, fetched and parsed.
     *
     * Takes a full URL because that is what the video already carries — the
     * captions are files under `/media`, not an endpoint keyed by video id, and
     * rebuilding the path here would be a second place that has to agree with
     * the server about where they live.
     *
     * Only iOS asks: Android hands the URL to ExoPlayer, which fetches and
     * draws the cues itself. See `VideoPlayer.rendersSubtitles`.
     */
    suspend fun subtitleCues(url: String): List<SubtitleCue>

    suspend fun comments(videoId: String): List<Comment>

    /**
     * Bring this video's comments in from YouTube.
     *
     * Called once when there are none, which is what the web app does — the
     * catalogue holds no comments for a video nobody has opened, so without this
     * every video would show an empty section for ever.
     */
    suspend fun importComments(videoId: String)

    /**
     * Fetch this video's metadata from the source again.
     *
     * Called when the row is missing something the screen wants to draw — today
     * that is the description, which most of the library has never had. It
     * returns nothing on purpose: what the caller needs afterwards is the video
     * row, and asking for that is [video].
     */
    suspend fun refreshMetadata(videoId: String)

    /** What to play after this one. */
    /**
     * What to play after this one, optionally narrowed to one channel.
     *
     * The channel is sent to the server rather than filtered here, because the
     * endpoint answers with a *different ranking* for it — not a subset of the
     * unfiltered one.
     */
    suspend fun upNext(videoId: String, channelId: String = ""): List<Video>

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

    /** Take this one out of the feed, and tell the ranker why. */
    suspend fun setNotInterested(videoId: String)

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

    /**
     * The member's playlists.
     *
     * A non-empty [videoId] also answers *which of them already hold that
     * video*, on every row. That is one question with two halves and it is one
     * call: the sheet cannot draw the ticks without the lists, and cannot draw
     * the lists without knowing which are ticked. Asking per playlist would be
     * N requests for one bit each.
     *
     * The saved shelf is **not** in this list. It is the pinned set — see
     * [Playlist] — and callers draw it themselves from [saved].
     */
    suspend fun playlists(videoId: String = ""): List<Playlist>

    /** One playlist and a page of what is in it. */
    suspend fun playlist(playlistId: String, pageToken: String = ""): PlaylistPage

    suspend fun createPlaylist(title: String): Playlist

    /**
     * Title and description together.
     *
     * One method rather than a rename, because creation already carries both:
     * a call that changes half of what creation set is one that grows a sibling
     * the first time somebody wants the other half.
     */
    suspend fun updatePlaylist(playlistId: String, title: String, description: String = ""): Playlist

    suspend fun deletePlaylist(playlistId: String)

    /** Adding a video the list already holds succeeds and changes nothing. */
    suspend fun addToPlaylist(playlistId: String, videoId: String)

    /** Removing one that is not there succeeds too, for the same reason. */
    suspend fun removeFromPlaylist(playlistId: String, videoId: String)
}

/**
 * A playlist and a page of its videos.
 *
 * One object rather than two calls, for [ChannelPage]'s reason: the page's
 * title and count come with the videos, and a header drawn from a second
 * request would arrive after the list it belongs to.
 */
data class PlaylistPage(
    val playlist: Playlist,
    val videos: List<Video>,
    val nextPageToken: String,
) {
    val hasMore: Boolean get() = nextPageToken.isNotEmpty()
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


/**
 * How Home is divided.
 *
 * The three shares are a preference and total 100 between them; the fixed part
 * is the server's and is only read. It is **one setting for the household**,
 * which is the server's decision, not this app's — so a phone changing it
 * changes everybody's Home.
 */
data class FeedMix(
    val subscribedPercent: Int,
    val affinityPercent: Int,
    val discoveryPercent: Int,
    /** Continue watching + rewatch + new uploads, as a percentage of the page. */
    val fixedPercent: Int,
) {
    /** What the three sliders divide between them. */
    val adjustablePercent: Int get() = 100 - fixedPercent
}
