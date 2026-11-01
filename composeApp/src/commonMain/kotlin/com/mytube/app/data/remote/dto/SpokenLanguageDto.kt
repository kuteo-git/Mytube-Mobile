package com.mytube.app.data.remote.dto

import com.mytube.app.domain.model.SpokenLanguage
import kotlinx.serialization.Serializable

/**
 * `GET /api/videos/{id}/spoken-language`: a primary subtag ("vi", "en"), or
 * empty when the server could not say.
 */
@Serializable
data class SpokenLanguageDto(val language: String? = null)

/** Only Vietnamese matters to narration; any other word is another language. */
fun SpokenLanguageDto.toDomain(): SpokenLanguage = when (language.orEmpty().lowercase()) {
    "" -> SpokenLanguage.Unknown
    "vi" -> SpokenLanguage.Vietnamese
    else -> SpokenLanguage.Other
}
