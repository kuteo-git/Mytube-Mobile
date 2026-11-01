package com.mytube.app.domain.model

import kotlin.jvm.JvmInline

/**
 * A video as this app reasons about it.
 *
 * Deliberately not the shape the gateway sends. The server's JSON carries fields
 * no screen here reads — ranking provenance, storage bookkeeping, the eviction
 * timestamps — and mapping at the edge means a field the server renames breaks
 * one file in `infrastructure` instead of every screen that touched it.
 *
 * No `@Serializable`. The moment a domain type knows how it is written to a
 * wire, the wire's shape and the app's logic are the same shape, and neither can
 * change without the other. `infrastructure` owns a separate DTO for that.
 */
data class Video(
    val id: String,
    val title: String,
    val channel: Channel,
    val durationSeconds: Int,
    val viewCount: Long,
    /**
     * ISO-8601, or empty when the server has none.
     *
     * Empty rather than null, and the rule is the project's: nothing in `domain`
     * or a ViewModel is nullable, because a type that can be absent everywhere
     * is checked nowhere. Absence is real here — 1,127 videos reached that
     * catalogue with no date — so it is named instead of implied: read
     * `hasPublishedDate`, never `publishedAt.isEmpty()` scattered at call sites.
     */
    val publishedAt: String,
    val thumbnailPath: String,
    /**
     * The YouTube address this came from, or empty.
     *
     * Carried because of the **channel page**, whose rows come from upstream
     * rather than from the catalogue — most of them have never been near this
     * disk, and opening one has to ask the server to write a row before there is
     * anything to play. The address, not an id this app formats into a URL: the
     * gateway's `POST /api/videos/external` takes one and already sends one, and
     * building it here would be guessing at a format that was handed over.
     *
     * See [ExternalVideo.sourceUrl], which says the same thing for the search
     * screen's half of this.
     */
    val sourceUrl: String = "",
    /**
     * Whether the catalogue has a row for this, and therefore whether the player
     * can be pointed at it.
     *
     * True by default because everything else in this app *is* a library video —
     * the feed, history, a playlist, the saved shelf. It is false only on the
     * channel page, and that is the whole reason it exists: without it, pressing
     * an upload the household has never imported navigated to a watch screen
     * that answered "gateway answered 404 for /api/videos/<id>".
     */
    val inLibrary: Boolean = true,
    /** Whether the household has kept this against the eviction sweep. */
    val saved: Boolean = false,
    val likeCount: Long = 0,
    /** Empty when the upload carried none. */
    val description: String = "",
    /** ISO-8601, or empty. When this video entered the library, not when it was published. */
    val addedAt: String = "",
    /**
     * The caption tracks the server holds for this video.
     *
     * Includes the machine translation the narration pass writes, which is why
     * a track carries `generated`: a viewer choosing between two Vietnamese
     * tracks needs to know which one a machine wrote.
     */
    val subtitles: List<SubtitleTrack> = emptyList(),
    val watchedFraction: Double = 0.0,
    /**
     * Where this viewer stopped, in seconds. The server's own number.
     *
     * Kept beside `watchedFraction` rather than derived from it. Multiplying the
     * fraction by the duration looks equivalent and is not: a video whose
     * duration the catalogue never learned — which is every video that arrived
     * through a flat listing — has `durationSeconds` 0, so the product is 0 and
     * the video silently restarts from the beginning every time.
     */
    val watchPositionSeconds: Int = 0,
    val reaction: Reaction = Reaction.None,
) {
    val hasPublishedDate: Boolean get() = publishedAt.isNotEmpty()

    /**
     * Started and not finished.
     *
     * The same 2%-to-95% window the server ranks by, copied rather than chosen:
     * a video must not be finished in one place and unfinished in another. Below
     * 2% is a video opened by mistake; above 95% is one whose credits are
     * rolling, and offering either as "continue watching" is offering to resume
     * something nobody is in the middle of.
     */
    val isInProgress: Boolean get() = watchedFraction > 0.02 && watchedFraction <= 0.95
}

/**
 * What this member said about a video.
 *
 * An enum rather than two booleans, because liking and disliking are exclusive
 * and a pair of flags can represent a state that cannot exist. The server sends
 * the same three words.
 */
enum class Reaction { None, Like, Dislike }

data class SubtitleTrack(
    /** BCP-47, as the server sends it: "vi", "en", "vi-x-mt". */
    val language: String,
    val label: String,
    /** A path under `/media`; absolute by the time it reaches a player. */
    val url: String,
    val generated: Boolean,
)

data class Comment(
    val id: String,
    val authorHandle: String,
    val avatarPath: String,
    val text: String,
    val publishedAt: String,
    val likeCount: Long,
    /**
     * Nested, one level.
     *
     * YouTube itself only nests once — a reply to a reply is still a reply to
     * the top comment — so a tree would be a shape nothing produces.
     */
    val replies: List<Comment> = emptyList(),
)

data class Channel(
    val id: String,
    val name: String,
    val handle: String,
    val avatarPath: String,
    /**
     * The wide picture across the top of a channel's own page, or empty.
     *
     * Empty on every channel that arrived through a listing — the feed sends one
     * per video and carries none — so the page that draws it must treat absence
     * as ordinary rather than as a hole to fill with grey.
     */
    val bannerPath: String = "",
    val subscribed: Boolean = false,
    /**
     * Zero when the server did not say.
     *
     * Zero rather than null, by the project's rule that nothing in `domain` is
     * nullable — and it costs nothing here, because a channel with no
     * subscribers and one whose count was not sent are drawn the same way: the
     * line is left off.
     */
    val subscriberCount: Long = 0,
)

/**
 * How far through a video somebody is, in seconds.
 *
 * Its own type rather than a bare `Double` because two different numbers in this
 * app are "a position in a video" — where the viewer is, and where a narration
 * clip begins — and the compiler should refuse to confuse them.
 */
@JvmInline
value class Seconds(val value: Double) {
    init {
        require(value >= 0) { "a position cannot be negative: $value" }
    }

    operator fun compareTo(other: Seconds): Int = value.compareTo(other.value)
}
