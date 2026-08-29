package com.mytube.app.data.remote.dto

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Topic
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
data class UserStateDto(
    @SerialName("watchProgress") val watchProgress: Double = 0.0,
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
    watchedFraction = userState?.watchProgress ?: 0.0,
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
    subscribed = subscribed,
    subscriberCount = subscriberCount,
)
