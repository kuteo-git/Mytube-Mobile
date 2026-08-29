package com.mytube.app.domain

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
    val publishedAt: String?,
    val thumbnailPath: String,
    /** Whether the household has kept this against the eviction sweep. */
    val saved: Boolean = false,
    val watchedFraction: Double = 0.0,
)

data class Channel(
    val id: String,
    val name: String,
    val handle: String,
    val avatarPath: String,
    val subscribed: Boolean = false,
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

    /**
     * The gap to another position, which may be negative and so is not a
     * Seconds.
     *
     * There was a `minus` operator returning Seconds here, and it was a trap: the
     * constructor refuses negatives, so subtracting a later position from an
     * earlier one threw instead of answering "behind by 3 seconds" — which is
     * exactly the question the narration scheduler asks on every frame.
     */
    fun gapTo(other: Seconds): Double = other.value - value
}
