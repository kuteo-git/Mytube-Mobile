package com.mytube.app.data

import com.mytube.app.data.remote.dto.SpokenLanguageDto
import com.mytube.app.data.remote.dto.toDomain
import com.mytube.app.domain.model.SpokenLanguage
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SpokenLanguageMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun read(body: String) = json.decodeFromString<SpokenLanguageDto>(body).toDomain()

    /** The gateway's three answers, and a field it did not send. */
    @Test
    fun theLanguageSpokenIsReadAsNarrationNeedsIt() {
        assertEquals(SpokenLanguage.Vietnamese, read("""{"language":"vi"}"""))
        assertEquals(SpokenLanguage.Other, read("""{"language":"en"}"""))
        assertEquals(SpokenLanguage.Unknown, read("""{"language":""}"""))
        assertEquals(SpokenLanguage.Unknown, read("""{}"""), "a gateway from before the route: not known")
    }
}
