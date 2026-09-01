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
    // Live is checked **before** hls, and the server never sends both: a
    // broadcast answers with the live tier alone, because offering hls beside it
    // would have the player climb toward a playlist built from adaptive tracks
    // the broadcast does not publish. The order is belt and braces, and it is
    // the honest one — a broadcast is a broadcast whatever else is in the
    // answer.
    //
    // No `?max=`. That parameter is read where the *ordinary* master playlist is
    // written; the live master is built by a different route that has never seen
    // it, and the charter's own measurement of a live ladder is 144p–720p — it
    // is already under a phone's ceiling.
    live != null && live.url.isNotBlank() -> Stream.Playable(
        url = baseUrl.trimEnd('/') + live.url,
        height = if (live.height > 0) live.height else maxHeight,
        isLive = true,
    )
    hls != null && hls.url.isNotBlank() -> Stream.Playable(
        url = baseUrl.trimEnd('/') + hls.url + capQuery(hls.url, maxHeight),
        height = if (hls.height > 0) hls.height else maxHeight,
    )
    // The file on the server's own disk, and **the last thing tried**.
    //
    // §2 of the charter put this in phase 3 and the consequence was measured on
    // the phone: a video the household had *downloaded* — `mediaState READY`, so
    // the server answers with this tier and nothing else — came back as "YouTube
    // will not hand this over". That is the same fault `live` had, in the same
    // place, and it is the worse half of it: the file is on disk, in this house,
    // and the app blamed an upstream refusal for it.
    //
    // Last rather than first because the tiers are not equivalent. HLS is
    // adaptive and carries the phone's 720 ceiling on the URL; this is one whole
    // file at whatever height was downloaded, usually 1080. On the house wifi
    // that is fine, and it is still the wrong default when the server has
    // offered a ladder.
    //
    // No `?max=`. There is nothing to cap: it is a file, not a playlist.
    local != null && local.url.isNotBlank() -> Stream.Playable(
        url = baseUrl.trimEnd('/') + local.url,
        height = if (local.height > 0) local.height else maxHeight,
    )
    else -> Stream.NothingPlayable
}

private fun capQuery(url: String, maxHeight: Int): String =
    if (maxHeight <= 0) "" else (if (url.contains('?')) "&" else "?") + "max=$maxHeight"
