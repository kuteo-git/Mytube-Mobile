package com.mytube.app.ui.watch

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.ui.cast.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The server writes the Vietnamese track while the first narration of a video
 * runs, so the list read when the video opened cannot hold it. Reported
 * 2026-11-07: the track was only offered the next time the video was opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchTranslatedTrackTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the translation is offered during the first narration of a video`() = runTest(dispatcher) {
        val pass = FirstLinesThenDone()
        val model = open(Library { pass.spoke }, pass)

        val languages = playing(model).video.subtitles.map { it.language }
        assertTrue("vi-x-mt" in languages, "offered only $languages")
    }

    @Test
    fun `a video that already has Vietnamese is not asked again`() = runTest(dispatcher) {
        val silent = Library { true }
        open(silent, RecordedPasses())
        val narrated = Library { true }
        open(narrated, FirstLinesThenDone())

        assertEquals(silent.asked, narrated.asked, "asked for the video again though it had a Vietnamese track")
    }

    private fun playing(model: WatchViewModel) = model.state.value as WatchState.Playing

    private fun TestScope.open(videos: Library, passes: NarrationRepository): WatchViewModel {
        val model = WatchViewModel(
            videoId = "first",
            startAtBeginning = true,
            autoPlay = false,
            onFinished = {},
            mediaBaseUrl = "http://mac:8180",
            videos = videos,
            streams = PlayableStreams,
            storyboards = NoStoryboards,
            narration = passes,
            preferences = Prefs(narration = true),
            openedFrom = emptyList(),
            maxHeight = PHONE_MAX_HEIGHT,
            playerFactory = SilentPlayers(),
        )
        advanceUntilIdle()
        return model
    }

    /** A pass that has spoken its first line, then finishes. */
    private class FirstLinesThenDone : NarrationRepository {
        private var polls = 0
        var spoke = false
        override suspend fun start(videoId: String, fromSeconds: Double) {}
        override suspend fun stop(videoId: String) {}
        override suspend fun state(videoId: String): Narration {
            polls++
            spoke = true
            val clip = NarrationClip(1.0, 2.0, "/media/$videoId/narration-tts/a.mp3", "Xin chào.")
            return Narration(
                status = if (polls < 3) NarrationStatus.Running else NarrationStatus.Done,
                done = 1, total = 2, clips = listOf(clip),
            )
        }
    }

    /** English captions, and the machine translation once [translated] says it is written. */
    private class Library(private val translated: () -> Boolean) : StubVideoRepository() {
        var asked = 0

        override suspend fun video(id: String): Video {
            asked++
            val english = SubtitleTrack("en", "English", "/media/$id/1080p.mp4.en.vtt", generated = true)
            val vietnamese = SubtitleTrack("vi-x-mt", "Tiếng Việt (dịch máy)", "/media/$id/1080p.mp4.vi-mt.vtt", generated = true)
            return Video(
                id = id, title = id,
                channel = Channel(id = "c", name = "Channel", handle = "@c", avatarPath = ""),
                durationSeconds = 100, viewCount = 0, publishedAt = "", thumbnailPath = "",
                inLibrary = true,
                subtitles = if (translated()) listOf(english, vietnamese) else listOf(english),
            )
        }

        override suspend fun upNext(videoId: String, channelId: String) = emptyList<Video>()
    }
}
