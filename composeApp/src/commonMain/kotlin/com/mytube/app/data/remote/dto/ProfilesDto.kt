package com.mytube.app.data.remote.dto

import com.mytube.app.domain.model.Profile
import kotlinx.serialization.Serializable

@Serializable
data class ProfilesDto(val profiles: List<ProfileDto> = emptyList())

@Serializable
data class ProfileDto(val id: String = "", val name: String = "")

fun ProfileDto.toDomain() = Profile(id = id, name = name)

/**
 * The household's speech settings, of which this app reads exactly one field.
 *
 * The endpoint answers with the base URL, the model, whether a key is set and a
 * hint of it. None of those belong on a phone: they are typed once, sitting at
 * the machine, and putting an API key field on a device that lives in a pocket
 * would be a promise this system does not keep. The voice is different — it is
 * the one thing here a listener has an opinion about while listening.
 *
 * Defaults on every field so a server that grows or drops one does not stop the
 * settings sheet from opening.
 */
@Serializable
data class TtsConfigDto(val voice: String = "")
