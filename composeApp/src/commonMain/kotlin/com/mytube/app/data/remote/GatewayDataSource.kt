package com.mytube.app.data.remote

import com.mytube.app.data.remote.dto.ChannelDetailDto
import com.mytube.app.data.remote.dto.ChannelVideosDto
import com.mytube.app.data.remote.dto.ChannelsDto
import com.mytube.app.data.remote.dto.CommentsDto
import com.mytube.app.data.remote.dto.FeedDto
import com.mytube.app.data.remote.dto.NarrationDto
import com.mytube.app.data.remote.dto.StreamDto
import com.mytube.app.data.remote.dto.TopicsDto
import com.mytube.app.data.remote.dto.VideoDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

/**
 * The one place this app makes an HTTP request.
 *
 * ## Why one place
 *
 * Every call has to say who is asking. The gateway reads `X-User-Id` and falls
 * back to a configured default when the header is absent, and the web app of
 * this same system spent its whole life without sending it — so every browser in
 * the household was one person, and one id accumulated 39,583 signals. The
 * lesson it wrote down afterwards is the reason this class exists:
 *
 *   *"Attached here rather than at each call site because 'every request' is the
 *   requirement, and a rule that has to be remembered forty times is a rule that
 *   will be forgotten once."*
 *
 * ## Why the address is a parameter and not a constructor argument
 *
 * It can change while the app is running — somebody fixes a typo on the settings
 * screen — and a base URL baked in at construction would mean rebuilding the
 * object graph to correct a mistake. The repository above reads the current one
 * and hands it down.
 */
class GatewayDataSource(private val client: HttpClient) {

    suspend fun feed(
        baseUrl: String,
        userId: String,
        topic: String,
        pageToken: String,
    ): FeedDto = client.get("${baseUrl.trimEnd('/')}/api/feed") {
        identify(userId)
        if (topic.isNotBlank()) parameter("topic", topic)
        if (pageToken.isNotBlank()) parameter("pageToken", pageToken)
    }.orThrow().body()

    suspend fun video(baseUrl: String, userId: String, id: String): VideoDto =
        client.get("${baseUrl.trimEnd('/')}/api/videos/$id") {
            identify(userId)
        }.orThrow().body()

    suspend fun search(baseUrl: String, userId: String, query: String): List<VideoDto> =
        client.get("${baseUrl.trimEnd('/')}/api/search") {
            identify(userId)
            parameter("q", query)
        }.orThrow().body<FeedDto>().videos

    suspend fun topics(baseUrl: String, userId: String): TopicsDto =
        client.get("${baseUrl.trimEnd('/')}/api/topics") { identify(userId) }.orThrow().body()

    suspend fun live(baseUrl: String, userId: String): FeedDto =
        client.get("${baseUrl.trimEnd('/')}/api/live") { identify(userId) }.orThrow().body()

    suspend fun history(baseUrl: String, userId: String, limit: Int): FeedDto =
        client.get("${baseUrl.trimEnd('/')}/api/history") {
            identify(userId)
            parameter("limit", limit)
        }.orThrow().body()

    /**
     * The channels this member follows.
     *
     * No page token, and the gateway offers none: the server charter reports one
     * household member following 152 channels and another 8, which is a list to
     * be read whole rather than paged.
     */
    suspend fun subscriptions(baseUrl: String, userId: String): ChannelsDto =
        client.get("${baseUrl.trimEnd('/')}/api/subscriptions") {
            identify(userId)
        }.orThrow().body()

    /**
     * How this video can be played right now.
     *
     * No `prefetch=1`: that flag means the viewer only hovered a card, and it
     * deliberately fetches no captions and queues no transfer. Pressing play is
     * a different question and must ask it properly — the web app spent a
     * release on exactly this confusion, with a hover's answer cached under the
     * player's own key so the real request was never issued.
     */
    suspend fun stream(baseUrl: String, userId: String, videoId: String): StreamDto =
        client.get("${baseUrl.trimEnd('/')}/api/videos/$videoId/stream") {
            identify(userId)
        }.orThrow().body()

    /**
     * What to play after this one.
     *
     * A separate question from the feed, and the server answers it differently:
     * per the charter, up-next damps taste to 0.35 so that *relatedness leads and
     * taste only breaks ties*. Asking the feed for a rail beside a playing video
     * would give a page of what somebody likes rather than of what follows.
     */
    suspend fun upNext(
        baseUrl: String,
        userId: String,
        videoId: String,
        channelId: String,
    ): FeedDto = client.get("${baseUrl.trimEnd('/')}/api/videos/$videoId/up-next") {
        identify(userId)
        if (channelId.isNotBlank()) parameter("channel", channelId)
    }.orThrow().body()

    suspend fun channel(baseUrl: String, userId: String, channelId: String): ChannelDetailDto =
        client.get("${baseUrl.trimEnd('/')}/api/channels/$channelId") {
            identify(userId)
        }.orThrow().body()

    suspend fun channelVideos(
        baseUrl: String,
        userId: String,
        channelId: String,
        sortToken: String,
        pageToken: String,
    ): ChannelVideosDto = client.get("${baseUrl.trimEnd('/')}/api/channels/$channelId/videos") {
        identify(userId)
        // Two different tokens with two different jobs: one picks the order,
        // the other continues within it. Sending an empty one would ask the
        // server to parse "" as a cursor.
        if (sortToken.isNotBlank()) parameter("sort", sortToken)
        if (pageToken.isNotBlank()) parameter("pageToken", pageToken)
    }.orThrow().body()

    /**
     * Ask the server to translate and speak this video.
     *
     * Answers 202 and returns at once — a full pass takes minutes, and a request
     * held open that long dies to a phone locking its screen, taking the pass
     * with it. Progress is read from [narration].
     */
    suspend fun startNarration(baseUrl: String, userId: String, videoId: String) {
        client.post("${baseUrl.trimEnd('/')}/api/videos/$videoId/narration") {
            identify(userId)
        }.orThrow()
    }

    suspend fun narration(baseUrl: String, userId: String, videoId: String): NarrationDto =
        client.get("${baseUrl.trimEnd('/')}/api/videos/$videoId/narration") {
            identify(userId)
        }.orThrow().body()

    /**
     * The video's comments, top-level with their replies.
     *
     * Read-only here. The gateway can post one, and this app does not: a
     * comment written from the sofa lands in this household's own catalogue and
     * never reaches YouTube, which is a thing worth building deliberately rather
     * than by putting a text field on a screen.
     */
    suspend fun comments(baseUrl: String, userId: String, videoId: String): CommentsDto =
        client.get("${baseUrl.trimEnd('/')}/api/videos/$videoId/comments") {
            identify(userId)
        }.orThrow().body()

    /**
     * Ask the server to import this video's comments from YouTube.
     *
     * Answers 200 with `{"imported":0,"unavailable":true}` when upstream
     * declines — never an error status. The server charter is explicit about
     * why: a temporary refusal on the one thing on this page that nothing
     * depends on used to turn the console red over a video that played fine.
     */
    suspend fun importComments(baseUrl: String, userId: String, videoId: String) {
        client.post("${baseUrl.trimEnd('/')}/api/videos/$videoId/comments/fetch") {
            identify(userId)
        }.orThrow()
    }

    /**
     * Where the viewer has got to.
     *
     * Fire and forget from the caller's point of view — nothing is returned, and
     * a failure must not interrupt playback. What it feeds is Continue watching
     * and the ranker's WATCH signal, both of which tolerate a missing report far
     * better than a viewer tolerates a stutter.
     */
    suspend fun recordProgress(
        baseUrl: String,
        userId: String,
        videoId: String,
        positionSeconds: Double,
        watchedFraction: Double,
    ) {
        client.post("${baseUrl.trimEnd('/')}/api/videos/$videoId/progress") {
            identify(userId)
            contentType(ContentType.Application.Json)
            setBody(ProgressBody(wholeSeconds(positionSeconds), watchedFraction))
        }.orThrow()
    }

    suspend fun setReaction(baseUrl: String, userId: String, videoId: String, reaction: String) {
        client.post("${baseUrl.trimEnd('/')}/api/videos/$videoId/reaction") {
            identify(userId)
            contentType(ContentType.Application.Json)
            setBody(ReactionBody(reaction))
        }.orThrow()
    }

    suspend fun setSaved(baseUrl: String, userId: String, videoId: String, saved: Boolean) {
        client.post("${baseUrl.trimEnd('/')}/api/videos/$videoId/pinned") {
            identify(userId)
            contentType(ContentType.Application.Json)
            setBody(PinnedBody(saved))
        }.orThrow()
    }

    /**
     * Tell the ranker this one was not wanted.
     *
     * A body-less POST: the server takes the video from the path and records a
     * dislike signal against it. Distinct from an actual dislike — the charter
     * keeps the two apart, since a dislike is a statement about the video and
     * this is a statement about the *recommendation*.
     */
    suspend fun setNotInterested(baseUrl: String, userId: String, videoId: String) {
        client.post("${baseUrl.trimEnd('/')}/api/videos/$videoId/not-interested") {
            identify(userId)
        }.orThrow()
    }

    suspend fun setSubscribed(
        baseUrl: String,
        userId: String,
        channelId: String,
        subscribed: Boolean,
    ) {
        client.post("${baseUrl.trimEnd('/')}/api/channels/$channelId/subscription") {
            identify(userId)
            contentType(ContentType.Application.Json)
            setBody(SubscribedBody(subscribed))
        }.orThrow()
    }

    /**
     * Whether something that behaves like the gateway answers here.
     *
     * Asks a real endpoint rather than the root: the gateway serves the web
     * bundle at `/`, so a 200 there proves only that *a* web server exists. A
     * feed request that parses is the shortest proof that this is the right one.
     */
    suspend fun reachable(baseUrl: String): Boolean = runCatching {
        client.get("${baseUrl.trimEnd('/')}/api/feed") {
            parameter("limit", 1)
        }.status.isSuccess()
    }.getOrElse { false }

    private fun io.ktor.client.request.HttpRequestBuilder.identify(userId: String) {
        // Omitted, never sent empty. Absence is what triggers the gateway's
        // fallback to a default profile, and that fallback is what keeps a fresh
        // install working before anybody has chosen who they are. An empty
        // header would be a claim to be nobody.
        if (userId.isNotBlank()) header("X-User-Id", userId)
    }
}

/**
 * A non-2xx answer is an error, said once.
 *
 * Ktor's default configuration returns the response rather than throwing, which
 * means every call site would have to remember to look. The server charter
 * records what that costs: an upstream status of 400 or more "arrives as a
 * *successful* round trip — `err` is nil, the status carries the bad news — so
 * passing it through left no trace anywhere. A day was spent looking at the
 * player for a fault that never logged a line."
 */
private fun HttpResponse.orThrow(): HttpResponse {
    if (!status.isSuccess()) throw GatewayException(status.value, call.request.url.toString())
    return this
}

/**
 * The bodies this app sends.
 *
 * Here rather than in `dto/` with the responses, because they are the shape of
 * *requests* and nothing maps to or from them: they are built from arguments and
 * written once.
 */
@kotlinx.serialization.Serializable
private data class ProgressBody(val positionSeconds: Long, val watchedFraction: Double)

/**
 * The position, as a whole number of seconds.
 *
 * The gateway declares `positionSeconds` as an **int32**, so a fractional value
 * is not a rounding difference — Go refuses the whole body with 400, and this
 * app was swallowing that. Measured: after forty-five seconds of playback the
 * server still held the position it had before the app was opened, and nothing
 * anywhere said why.
 *
 * Kept out of the request builder and given a name so a test can assert it
 * without a network. A player legitimately reports a small negative position
 * before it has loaded, and a negative position is not a thing the catalogue
 * should be asked to store.
 */
internal fun wholeSeconds(value: Double): Long =
    if (value <= 0) 0 else kotlin.math.round(value).toLong()

@kotlinx.serialization.Serializable
private data class ReactionBody(val reaction: String)

@kotlinx.serialization.Serializable
private data class PinnedBody(val pinned: Boolean)

@kotlinx.serialization.Serializable
private data class SubscribedBody(val subscribed: Boolean)

class GatewayException(val status: Int, val url: String) :
    Exception("gateway answered $status for $url")
