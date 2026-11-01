package com.mytube.app.data.remote.dto

import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastDeviceKind
import com.mytube.app.domain.model.CastEnd
import com.mytube.app.domain.model.CastHandback
import com.mytube.app.domain.model.CastItem
import com.mytube.app.domain.model.CastNarration
import com.mytube.app.domain.model.CastSession
import com.mytube.app.domain.model.CastState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The gateway's cast shapes (services/gateway/internal/api/cast.go), snake_case as it writes them. */
@Serializable
data class CastDeviceDto(
    val id: String? = null,
    val name: String? = null,
    val kind: String? = null,
)

@Serializable
data class CastItemDto(
    @SerialName("video_id") val videoId: String? = null,
    val title: String? = null,
    val channel: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("duration_s") val durationSeconds: Int? = null,
)

@Serializable
data class CastSessionDto(
    val id: String? = null,
    val device: CastDeviceDto? = null,
    val state: String? = null,
    @SerialName("ended_reason") val endedReason: String? = null,
    val queue: List<CastItemDto>? = null,
    @SerialName("current_index") val currentIndex: Int? = null,
    @SerialName("position_s") val positionSeconds: Int? = null,
    @SerialName("duration_s") val durationSeconds: Int? = null,
    val volume: Int? = null,
    val version: Int? = null,
    @SerialName("narration_status") val narrationStatus: String? = null,
)

@Serializable
data class CastHandbackDto(
    @SerialName("video_id") val videoId: String? = null,
    @SerialName("position_s") val positionSeconds: Int? = null,
)

fun CastDeviceDto.toDomain(): CastDevice = CastDevice(
    id = id.orEmpty(),
    name = name.orEmpty(),
    kind = if (kind == "google") CastDeviceKind.Google else CastDeviceKind.R1,
)

fun CastItemDto.toDomain(): CastItem = CastItem(
    videoId = videoId.orEmpty(),
    title = title.orEmpty(),
    channel = channel.orEmpty(),
    thumbnailUrl = thumbnailUrl.orEmpty(),
    durationSeconds = durationSeconds ?: 0,
)

private val states = mapOf(
    "loading" to CastState.Loading,
    "playing" to CastState.Playing,
    "paused" to CastState.Paused,
    "ended" to CastState.Ended,
)

private val narrations = mapOf(
    "preparing" to CastNarration.Preparing,
    "on" to CastNarration.On,
    "unavailable" to CastNarration.Unavailable,
)

private val ends = mapOf(
    "queue_finished" to CastEnd.QueueFinished,
    "stopped" to CastEnd.Stopped,
    "replaced" to CastEnd.Replaced,
    "taken_over" to CastEnd.TakenOver,
    "stopped_on_device" to CastEnd.StoppedOnDevice,
    "device_offline" to CastEnd.DeviceOffline,
    "handoff" to CastEnd.Handoff,
    "failed" to CastEnd.Failed,
)

fun CastSessionDto.toDomain(): CastSession {
    val state = states[state] ?: CastState.Loading
    return CastSession(
        id = id.orEmpty(),
        device = (device ?: CastDeviceDto()).toDomain(),
        state = state,
        // An ended session with a reason this build does not know is still
        // ended; Stopped is the end that says nothing on screen.
        end = endedReason?.let { ends[it] ?: CastEnd.Stopped }
            ?: if (state == CastState.Ended) CastEnd.Stopped else CastEnd.Running,
        queue = queue.orEmpty().map { it.toDomain() },
        currentIndex = currentIndex ?: 0,
        positionSeconds = positionSeconds ?: 0,
        durationSeconds = durationSeconds ?: 0,
        volume = volume ?: 0,
        version = version ?: 0,
        // A gateway from before narration on speakers sends nothing: off.
        narration = narrations[narrationStatus] ?: CastNarration.Off,
    )
}

fun CastHandbackDto.toDomain(): CastHandback = CastHandback(videoId.orEmpty(), positionSeconds ?: 0)
