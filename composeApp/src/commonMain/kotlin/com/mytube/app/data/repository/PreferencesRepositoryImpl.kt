package com.mytube.app.data.repository

import com.mytube.app.data.local.SettingsDataSource
import com.mytube.app.data.local.SettingsKeys
import com.mytube.app.domain.repository.PreferencesRepository

/**
 * The playback preferences, on the device.
 *
 * Thin on purpose: every method reads or writes one key. The only judgement is
 * what an absent key means, and it is the same for all three — off, and no
 * subtitles. A device nobody has set up should play a video the way YouTube
 * does, not with a voice over it.
 */
class PreferencesRepositoryImpl(
    private val settings: SettingsDataSource,
) : PreferencesRepository {

    override suspend fun narration(): Boolean = read(SettingsKeys.NARRATION)

    override suspend fun setNarration(on: Boolean) = write(SettingsKeys.NARRATION, on)

    override suspend fun autoplay(): Boolean = read(SettingsKeys.AUTOPLAY)

    override suspend fun setAutoplay(on: Boolean) = write(SettingsKeys.AUTOPLAY, on)

    override suspend fun subtitleLanguage(): String =
        settings.get(SettingsKeys.SUBTITLE_LANGUAGE).orEmpty()

    override suspend fun setSubtitleLanguage(language: String) =
        settings.set(SettingsKeys.SUBTITLE_LANGUAGE, language)

    // Stored as words rather than "0"/"1". The file is meant to be readable by
    // somebody looking at why a phone is behaving oddly.
    private suspend fun read(key: String): Boolean = settings.get(key) == "true"

    private suspend fun write(key: String, on: Boolean) = settings.set(key, on.toString())
}
