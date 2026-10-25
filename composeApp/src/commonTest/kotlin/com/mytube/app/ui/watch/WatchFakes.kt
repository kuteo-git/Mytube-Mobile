package com.mytube.app.ui.watch

import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.StoryboardState
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.PreferencesRepository
import com.mytube.app.domain.repository.StoryboardRepository

// The watch tests' neutral collaborators, shared: four files each carried the
// same three, and none of these tests is about what they answer.

/**
 * No preview, which is what most of this library answers.
 *
 * None of these tests is about storyboards, so the honest stub is the answer a
 * video with no ladder really gets — a test that accidentally reaches this
 * takes the path a Short takes rather than one nothing takes.
 */
internal object NoStoryboards : StoryboardRepository {
    override suspend fun storyboard(videoId: String) = StoryboardState.None
}

/** No narration pass, running or finished. */
internal object IdleNarration : NarrationRepository {
    override suspend fun start(videoId: String, fromSeconds: Double) {}
    override suspend fun stop(videoId: String) {}
    override suspend fun state(videoId: String) =
        Narration(NarrationStatus.Idle, done = 0, total = 0, clips = emptyList())
}

/** Every preference at its default, answered at once. */
internal object InstantPreferences : PreferencesRepository {
    override suspend fun narration() = false
    override suspend fun setNarration(on: Boolean) {}
    override suspend fun autoplay() = false
    override suspend fun setAutoplay(on: Boolean) {}
    override suspend fun subtitleLanguage() = ""
    override suspend fun setSubtitleLanguage(language: String) {}
    override suspend fun voiceLevel() = 1f
    override suspend fun setVoiceLevel(level: Float) {}
    override suspend fun duckLevel() = 0.2f
    override suspend fun setDuckLevel(level: Float) {}
    override suspend fun lastVideoId() = ""
    override suspend fun setLastVideoId(id: String) {}
}
