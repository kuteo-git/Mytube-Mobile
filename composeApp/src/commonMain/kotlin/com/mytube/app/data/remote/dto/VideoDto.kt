package com.mytube.app.data.remote.dto

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.Video
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The gateway's JSON, exactly as it arrives.
 *
 * Modelled from real responses rather than from the Go structs, because what
 * matters is what is on the wire. Fields the app does not read are simply
 * absent here: `kotlinx.serialization` ignores unknown keys when the Json
 * instance says so, so listing only what is used keeps this file honest about
 * what the app depends on.
 *
 * ## Why this is not the domain type
 *
 * Every field here can be renamed by a server release. Keeping the two apart
 * means such a rename breaks `toDomain` and nothing else. It also keeps
 * `@Serializable` out of `domain`, which `ArchitectureGuardTest` enforces —
 * a domain type carrying a wire annotation has the wire's shape, and neither can
 * then change alone.
 */
@Serializable
data class VideoDto(
    val id: String,
    val title: String = "",
    val channel: ChannelDto = ChannelDto(),
    val durationSeconds: Int = 0,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val description: String = "",
    val addedAt: String = "",
    val subtitles: List<SubtitleDto> = emptyList(),
    val publishedAt: String? = null,
    val thumbnailPath: String = "",
    /**
     * The household-wide "keep this file" flag as the server calls it.
     *
     * Named `pinned` on the wire and `saved` in the domain, deliberately: the
     * server charter separates the two — Save is a personal shelf, pinning is a
     * fact about the disk — and settled on sending the *viewer's* answer under
     * the older name. The rename happens here so nothing downstream inherits
     * the confusion.
     */
    val pinned: Boolean = false,
    val userState: UserStateDto? = null,
)

@Serializable
data class ChannelDto(
    val id: String = "",
    val name: String = "",
    val handle: String = "",
    val avatarPath: String = "",
    val bannerPath: String = "",
    val subscribed: Boolean = false,
    val subscriberCount: Long = 0,
)

/**
 * The answer to `GET /api/subscriptions`.
 *
 * Its own wrapper because the gateway answers `{"channels": [...]}` rather than
 * a bare array — the same shape as the feed, and for the same reason: a top-level
 * array leaves nowhere to add a page token later without breaking every reader.
 */
@Serializable
data class ChannelsDto(
    val channels: List<ChannelDto> = emptyList(),
)

@Serializable
data class SubtitleDto(
    val language: String = "",
    val label: String = "",
    val url: String = "",
    val generated: Boolean = false,
)

@Serializable
data class UserStateDto(
    @SerialName("watchProgress") val watchProgress: Double = 0.0,
    val watchPositionSeconds: Int = 0,
    /** "LIKE", "DISLIKE", or absent. */
    val reaction: String = "",
)

/**
 * Nullable here, and only here.
 *
 * This is the boundary: the wire really can omit a field, and pretending
 * otherwise would mean a parse failure for an answer that is perfectly legal.
 * Everything inward of `toDomain` is non-null, so absence is decided once,
 * in the mapper, rather than re-checked at every screen that reads it.
 */
@Serializable
data class TopicDto(val name: String = "", val videoCount: Int = 0)

@Serializable
data class TopicsDto(val topics: List<TopicDto> = emptyList())

fun TopicDto.toDomain(): Topic = Topic(name = name, videoCount = videoCount)

@Serializable
data class FeedDto(
    val videos: List<VideoDto> = emptyList(),
    val nextPageToken: String? = null,
)

fun VideoDto.toDomain(): Video = Video(
    id = id,
    title = title,
    channel = channel.toDomain(),
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    publishedAt = publishedAt.orEmpty(),
    thumbnailPath = thumbnailPath,
    saved = pinned,
    likeCount = likeCount,
    description = description,
    addedAt = addedAt,
    subtitles = subtitles
        // A track with no address cannot be loaded, and offering it would put a
        // row in the menu that turns subtitles off when pressed.
        .filter { it.url.isNotEmpty() }
        .map { SubtitleTrack(it.language, it.label, it.url, it.generated) },
    watchedFraction = userState?.watchProgress ?: 0.0,
    watchPositionSeconds = userState?.watchPositionSeconds ?: 0,
    reaction = when (userState?.reaction) {
        "LIKE" -> Reaction.Like
        "DISLIKE" -> Reaction.Dislike
        // Anything else — absent, empty, a word a later server release invents
        // — is no reaction. A value this app does not recognise must not be
        // drawn as a lit button.
        else -> Reaction.None
    },
)

fun ChannelDto.toDomain(): Channel = Channel(
    id = id,
    name = name,
    handle = handle,
    avatarPath = avatarPath,
    bannerPath = bannerPath,
    subscribed = subscribed,
    subscriberCount = subscriberCount,
)

@Serializable
data class ChannelDetailDto(
    val channel: ChannelDto = ChannelDto(),
    val videoCount: Int = 0,
)

/**
 * One of a channel's uploads, as the channel endpoint sends it.
 *
 * A different shape from [VideoDto] and deliberately not merged with it. This
 * comes from **YouTube**, not the catalogue — the gateway asks upstream because
 * a scan only brings in the newest few dozen uploads, so serving the page from
 * the catalogue would cap a channel at that number for reasons the viewer cannot
 * see. The consequences are visible in the fields: an absolute `thumbnailUrl`
 * rather than a path under `/media`, no channel of its own, and no user state,
 * because most of these have never been near this disk.
 */
@Serializable
data class ChannelVideoDto(
    val id: String,
    /** The address to hand back when the household has never imported this. */
    val sourceUrl: String = "",
    val title: String = "",
    val durationSeconds: Int = 0,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val description: String = "",
    val addedAt: String = "",
    val subtitles: List<SubtitleDto> = emptyList(),
    val thumbnailUrl: String = "",
    val publishedAt: String? = null,
    val inLibrary: Boolean = false,
)

@Serializable
data class SortOptionDto(val label: String = "", val token: String = "")

@Serializable
data class ChannelVideosDto(
    val videos: List<ChannelVideoDto> = emptyList(),
    val sortOptions: List<SortOptionDto> = emptyList(),
    val nextPageToken: String = "",
)

/**
 * An upstream listing, given this page's channel.
 *
 * The channel is supplied by the caller because the endpoint does not send one
 * per row — it is the same channel for every row on the page, and repeating it
 * on each would be the server sending the same object fifty times.
 */
fun ChannelVideoDto.toDomain(channel: Channel): Video = Video(
    id = id,
    title = title,
    channel = channel,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    publishedAt = publishedAt.orEmpty(),
    // Absolute, and `imageModel` is what notices. See its comment.
    thumbnailPath = thumbnailUrl,
    // The two facts that make an upstream row openable. Everywhere else in the
    // app these take their defaults, because everywhere else a Video is a row
    // that already exists on this disk.
    sourceUrl = sourceUrl,
    inLibrary = inLibrary,
)

@Serializable
data class NarrationDto(
    val status: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val error: String = "",
    /** Named `cues` on the wire; they are clips by the time they arrive here. */
    val cues: List<NarrationClipDto> = emptyList(),
)

@Serializable
data class NarrationClipDto(
    val startSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val clipUrl: String = "",
    val text: String = "",
    /** Set only for a broadcast, whose lines are placed by the clock. */
    val startsAtUnixMillis: Long = 0,
)

/**
 * The manifest, with clip addresses made absolute.
 *
 * The server sends a path under `/media`, because that is what it knows. A
 * player needs somewhere to fetch it from, and only this side knows which
 * machine the library is on.
 */
fun NarrationDto.toDomain(baseUrl: String): Narration = Narration(
    status = NarrationStatus.fromWire(status),
    done = done,
    total = total,
    error = error,
    clips = cues
        // A clip with no address is not playable and must not reach the
        // scheduler, which would otherwise duck the video for a line that never
        // speaks.
        .filter { it.clipUrl.isNotEmpty() }
        .map {
            NarrationClip(
                startSeconds = it.startSeconds,
                durationSeconds = it.durationSeconds,
                clipUrl = baseUrl.trimEnd('/') + it.clipUrl,
                text = it.text,
                startsAtEpochMillis = it.startsAtUnixMillis,
            )
        },
)


@Serializable
data class CommentsDto(
    val comments: List<CommentDto> = emptyList(),
    val nextPageToken: String = "",
)

@Serializable
data class CommentDto(
    val id: String = "",
    val authorHandle: String = "",
    val avatarPath: String = "",
    val text: String = "",
    val publishedAt: String = "",
    val likeCount: Long = 0,
    val replies: List<CommentDto> = emptyList(),
)

fun CommentDto.toDomain(): Comment = Comment(
    id = id,
    authorHandle = authorHandle,
    avatarPath = avatarPath,
    text = text,
    publishedAt = publishedAt,
    likeCount = likeCount,
    replies = replies.map { it.toDomain() },
)


@Serializable
data class FeedMixDto(
    val subscribedPercent: Int = 0,
    val affinityPercent: Int = 0,
    val discoveryPercent: Int = 0,
    val fixedShares: FixedSharesDto = FixedSharesDto(),
)

/**
 * The parts of the page the sliders do **not** divide.
 *
 * Sent by the server rather than known here, and that is deliberate: the web app
 * carried its own copy once and spent a release quoting a figure that had gone
 * stale when a new fixed share took ten per cent of the page.
 */
@Serializable
data class FixedSharesDto(
    val continueWatching: Int = 0,
    val rewatch: Int = 0,
    val freshSubscribed: Int = 0,
)

/**
 * One result from `GET /api/discover` — a video on YouTube.
 *
 * Every field defaulted, as every DTO here is: the wire really can omit one, and
 * a parse failure is the worst possible answer to a legal response.
 */
@Serializable
data class ExternalVideoDto(
    val id: String = "",
    val title: String = "",
    val channelName: String = "",
    val durationSeconds: Int = 0,
    val viewCount: Long = 0,
    val thumbnailUrl: String = "",
    val sourceUrl: String = "",
    val inLibrary: Boolean = false,
)

@Serializable
data class DiscoverDto(val videos: List<ExternalVideoDto> = emptyList())

/**
 * The wire's shape into the app's.
 *
 * Nothing is computed here and that is the point: the gateway sends exactly the
 * eight fields this screen draws, so the mapper's whole job is to stop the
 * `@Serializable` annotation from travelling any further inward.
 */
fun ExternalVideoDto.toDomain(): ExternalVideo = ExternalVideo(
    id = id,
    title = title,
    channelName = channelName,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    thumbnailUrl = thumbnailUrl,
    sourceUrl = sourceUrl,
    inLibrary = inLibrary,
)

/** The gateway's answer to "make a row for this so the player can open it". */
@Serializable
data class EnsureExternalRequest(val url: String)

@Serializable
data class EnsureExternalDto(val videoId: String = "")

/**
 * `GET /api/channels/resolve` — which channel a pasted address names.
 *
 * **Nullable, and that is the answer.** The gateway is asked about every query
 * and most queries are not addresses, so it replies `{"channel":null}` rather
 * than with an error. This is a DTO, where absence is allowed to be real; the
 * mapper turns it into an empty string, which is what the app reasons about.
 */
@Serializable
data class ResolveChannelDto(val channel: String? = null)

/**
 * `GET /api/playlists` and `GET /api/playlists/{id}` — one collection.
 *
 * `containsVideo` is only meaningful when the request carried `?videoId=`; the
 * gateway sends false otherwise, and the sheet is the only caller that asks.
 */
@Serializable
data class PlaylistDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val itemCount: Int = 0,
    val thumbnails: List<String> = emptyList(),
    val containsVideo: Boolean = false,
)

@Serializable
data class PlaylistsDto(val playlists: List<PlaylistDto> = emptyList())

/** A playlist and a page of what is in it. */
@Serializable
data class PlaylistPageDto(
    val playlist: PlaylistDto = PlaylistDto(),
    val videos: List<VideoDto> = emptyList(),
    val nextPageToken: String = "",
)

fun PlaylistDto.toDomain(): Playlist = Playlist(
    id = id,
    title = title,
    description = description,
    itemCount = itemCount,
    containsVideo = containsVideo,
    thumbnailPaths = thumbnails,
)

/** Create and rename take the same body: the server has one RPC for both. */
@Serializable
data class PlaylistBody(val title: String, val description: String = "")

@Serializable
data class PlaylistItemBody(val videoId: String)
