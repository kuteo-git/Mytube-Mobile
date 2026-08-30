package com.mytube.app.data.repository

import com.mytube.app.data.local.SettingsDataSource
import com.mytube.app.data.local.SettingsKeys
import com.mytube.app.domain.model.DEFAULT_DUCK_LEVEL
import com.mytube.app.domain.model.DEFAULT_VOICE_LEVEL
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

    override suspend fun voiceLevel(): Float =
        level(SettingsKeys.VOICE_LEVEL, DEFAULT_VOICE_LEVEL)

    override suspend fun setVoiceLevel(level: Float) =
        settings.set(SettingsKeys.VOICE_LEVEL, level.toString())

    override suspend fun duckLevel(): Float =
        level(SettingsKeys.DUCK_LEVEL, DEFAULT_DUCK_LEVEL)

    override suspend fun setDuckLevel(level: Float) =
        settings.set(SettingsKeys.DUCK_LEVEL, level.toString())

    override suspend fun lastVideoId(): String =
        settings.get(SettingsKeys.LAST_VIDEO).orEmpty()

    override suspend fun setLastVideoId(id: String) =
        settings.set(SettingsKeys.LAST_VIDEO, id)

    /**
     * A stored level, or the default.
     *
     * `toFloatOrNull` rather than `toFloat`: the file is meant to be readable
     * and therefore editable, and a hand-typed "0,5" must leave the app playing
     * rather than crash it on the first video. The clamp is the same argument —
     * a level of 40 written by hand is a stored value, not a reason to deafen
     * somebody.
     */
    private suspend fun level(key: String, fallback: Float): Float =
        settings.get(key)?.toFloatOrNull()?.coerceIn(0f, MAX_LEVEL) ?: fallback

    // Stored as words rather than "0"/"1". The file is meant to be readable by
    // somebody looking at why a phone is behaving oddly.
    private suspend fun read(key: String): Boolean = settings.get(key) == "true"

    private suspend fun write(key: String, on: Boolean) = settings.set(key, on.toString())

    private companion object {
        /**
         * The ceiling both sliders share.
         *
         * 2.0 rather than 1.0 because the voice legitimately wants to be louder
         * than the source it is read over — the web app allows the same, and a
         * limiter sits after it. Applying the same ceiling to the duck level
         * costs nothing and means one number to reason about.
         */
        const val MAX_LEVEL = 2f
    }
}
