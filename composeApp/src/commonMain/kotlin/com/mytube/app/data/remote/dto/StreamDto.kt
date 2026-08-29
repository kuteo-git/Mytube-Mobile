package com.mytube.app.data.remote.dto

import com.mytube.app.domain.model.Stream
import kotlinx.serialization.Serializable

/**
 * The gateway's answer to "how can I play this".
 *
 * Every tier is nullable here because the server genuinely omits the ones that
 * do not apply — a video with no local copy has no `local` key at all. That is
 * the boundary doing its job; [toDomain] closes all of it.
 */
@Serializable
data class StreamDto(
    val hls: SourceDto? = null,
    val local: SourceDto? = null,
    val live: SourceDto? = null,
    val upcoming: Boolean = false,
    val unavailable: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class SourceDto(
    val url: String = "",
    val height: Int = 0,
)

/**
 * The device ceiling travels on the URL.
 *
 * `?max=720` is read by the server when it writes the master playlist, and it
 * exists because on iOS a cap **cannot** be applied in the client: HLS plays
 * natively there and a page has no way to limit a level. Writing a shorter
 * playlist is the only place the ceiling can be enforced, so both platforms use
 * the same mechanism rather than one of them being special.
 */
fun StreamDto.toDomain(baseUrl: String, maxHeight: Int): Stream = when {
    upcoming -> Stream.Upcoming
    unavailable -> Stream.Unavailable(reason.orEmpty().ifBlank { "unavailable" })
    hls != null && hls.url.isNotBlank() -> Stream.Playable(
        url = baseUrl.trimEnd('/') + hls.url + capQuery(hls.url, maxHeight),
        height = if (hls.height > 0) hls.height else maxHeight,
    )
    else -> Stream.NothingPlayable
}

private fun capQuery(url: String, maxHeight: Int): String =
    if (maxHeight <= 0) "" else (if (url.contains('?')) "&" else "?") + "max=$maxHeight"
