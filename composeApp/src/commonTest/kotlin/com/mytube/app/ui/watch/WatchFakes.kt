package com.mytube.app.ui.watch

import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.StoryboardState
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.PreferencesRepository
import com.mytube.app.domain.repository.StoryboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import com.mytube.app.domain.repository.VideoPlayerFactory
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.NarrationClip

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

/** Every video plays, from the one HLS address. */
internal object PlayableStreams : StreamRepository {
    override suspend fun stream(videoId: String, maxHeight: Int): Stream =
        Stream.Playable(url = "http://mac:8180/hls/$videoId/master.m3u8", height = 720)
}

/** Players that do nothing, kept so a test can set what one reports. */
internal class SilentPlayers : VideoPlayerFactory {
    val made = mutableListOf<SilentPlayer>()
    override fun create(): VideoPlayer = SilentPlayer().also { made += it }
}

internal class SilentPlayer : VideoPlayer {
    override val state = MutableStateFlow(PlaybackState())
    override val rendersSubtitles = false
    override fun load(media: PlayingMedia, startAtSeconds: Double) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(seconds: Double) {}
    override fun narrate(clips: List<NarrationClip>) {}
    override fun setNarrationLevels(voice: Float, duck: Float) {}
    override fun showSubtitles(language: String) {}
    override fun stop() {}
    override fun release() {}
}

/** Every pass asked of the server, each with nothing to say. */
internal class RecordedPasses : NarrationRepository {
    val started = mutableListOf<String>()
    val from = mutableListOf<Double>()
    override suspend fun start(videoId: String, fromSeconds: Double) {
        started += videoId
        from += fromSeconds
    }
    override suspend fun stop(videoId: String) {}
    override suspend fun state(videoId: String) =
        Narration(NarrationStatus.Done, done = 0, total = 0, clips = emptyList())
}

/** Every video a broadcast on air. */
internal object LiveStreams : StreamRepository {
    override suspend fun stream(videoId: String, maxHeight: Int): Stream =
        Stream.Playable(url = "http://mac:8180/live/$videoId.m3u8", height = 720, isLive = true)
}

