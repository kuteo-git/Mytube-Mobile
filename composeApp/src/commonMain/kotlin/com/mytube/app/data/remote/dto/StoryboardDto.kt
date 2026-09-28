package com.mytube.app.data.remote.dto

import com.mytube.app.domain.model.Storyboard
import kotlinx.serialization.Serializable

/**
 * The gateway's answer to "what can I draw while scrubbing".
 *
 * Every field has a default because the wire really can omit one, and §3 puts
 * that decision here rather than in a screen. What the defaults produce is a
 * board whose `isDrawable` is false, which the mapper turns into "no preview" —
 * the same answer as a 404, because a board naming no sheets and a video with no
 * sheets are the same thing to anybody drawing one.
 */
@Serializable
data class StoryboardDto(
    val tileWidth: Int = 0,
    val tileHeight: Int = 0,
    val rows: Int = 0,
    val columns: Int = 0,
    val intervalSeconds: Double = 0.0,
    val sprites: List<String> = emptyList(),
)

/**
 * Domain types are not the server's JSON, even when the two happen to match.
 *
 * They match today and the mapping is one-to-one, which is not a reason to skip
 * it: the moment this carried `@Serializable` into `domain`, the wire's shape and
 * the arithmetic's shape would be one shape and neither could change alone. The
 * charter's rule, and the reason `Video` has a mapper it barely needs either.
 */
fun StoryboardDto.toDomain(): Storyboard = Storyboard(
    tileWidth = tileWidth,
    tileHeight = tileHeight,
    rows = rows,
    columns = columns,
    intervalSeconds = intervalSeconds,
    sprites = sprites,
)
