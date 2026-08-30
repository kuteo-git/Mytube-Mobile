package com.mytube.app.data.local

/**
 * The handful of values this device remembers between launches.
 *
 * ## Why an interface with platform implementations
 *
 * There is no multiplatform key-value store in the standard library, and the two
 * platforms have perfectly good ones of their own — `SharedPreferences` and
 * `NSUserDefaults`. Pulling in a library to paper over a difference this small
 * would be a dependency to keep alive for two `expect/actual` methods.
 *
 * ## Why `suspend`
 *
 * Not because either platform's store is slow — both are effectively immediate.
 * It is because a caller that is allowed to assume "immediate" will read this on
 * the main thread from a composable, and the day a value moves to a file or a
 * keychain, every one of those call sites becomes a fault that has to be found.
 * Suspending from the start costs nothing and closes that door.
 */
interface SettingsDataSource {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
}

/**
 * The keys, named once.
 *
 * Written out as constants rather than typed at each call site: a settings key
 * misspelled at one of two call sites reads back nothing and writes to a slot
 * nobody looks at, which is a bug that looks exactly like "the setting did not
 * save".
 */
object SettingsKeys {
    const val BASE_URL = "server.baseUrl"
    const val PROFILE_ID = "profile.id"
    const val LANGUAGE = "ui.language"
    const val NARRATION = "player.narration"
    const val AUTOPLAY = "player.autoplay"
    const val SUBTITLE_LANGUAGE = "player.subtitleLanguage"
    const val VOICE_LEVEL = "player.voiceLevel"
    const val DUCK_LEVEL = "player.duckLevel"
    const val LAST_VIDEO = "player.lastVideoId"
}
