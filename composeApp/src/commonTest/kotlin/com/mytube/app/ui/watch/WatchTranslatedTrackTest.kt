package com.mytube.app.ui.watch

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.PreferencesRepository
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

    /**
     * Reported 2026-11-07: Vietnamese chosen on a new video showed a while, then
     * nothing, until English and back. The server rewrites the file after every
     * batch it translates, and the lines were read once, when it was chosen.
     */
    @Test
    fun `a Vietnamese track chosen while it is written shows the lines written after`() = runTest(dispatcher) {
        val pass = TranslatesInBatches()
        val library = Library { pass.polls > 0 }.apply { written = pass::written }
        val model = open(library, pass, Prefs(narration = true, subtitles = "vi-x-mt"))

        val shown = playing(model)
        assertEquals("vi-x-mt", shown.subtitleLanguage, "the remembered Vietnamese was not chosen")
        assertEquals(pass.written(), shown.subtitleCues.map { it.text }, "the lines translated after it was chosen are missing")
    }

    private fun playing(model: WatchViewModel) = model.state.value as WatchState.Playing

    private fun Prefs(narration: Boolean) = Prefs(narration, subtitles = "")

    private fun TestScope.open(
        videos: Library,
        passes: NarrationRepository,
        preferences: Prefs = Prefs(narration = true),
    ): WatchViewModel {
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
            preferences = preferences,
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

    /** Translates a batch on each of its first polls, then finishes. */
    private class TranslatesInBatches : NarrationRepository {
        private val batches = listOf("Xin chào.", "Hôm nay trời đẹp.", "Tạm biệt.")
        var polls = 0
        fun written() = batches.take(polls.coerceAtMost(batches.size))
        override suspend fun start(videoId: String, fromSeconds: Double) {}
        override suspend fun stop(videoId: String) {}
        override suspend fun state(videoId: String): Narration {
            polls++
            val clips = written().mapIndexed { i, line ->
                NarrationClip(i * 20.0, i * 20.0 + 2.0, "/media/$videoId/narration-tts/$i.mp3", line)
            }
            return Narration(
                status = if (polls < batches.size) NarrationStatus.Running else NarrationStatus.Done,
                done = clips.size, total = batches.size, clips = clips,
            )
        }
    }

    private class Prefs(narration: Boolean, private val subtitles: String) : PreferencesRepository by com.mytube.app.ui.cast.Prefs(narration) {
        override suspend fun subtitleLanguage() = subtitles
    }

    /** English captions, and the machine translation once [translated] says it is written. */
    private class Library(private val translated: () -> Boolean) : StubVideoRepository() {
        var asked = 0
        var written: () -> List<String> = { emptyList() }

        override suspend fun subtitleCues(url: String) =
            if (url.endsWith(".vi-mt.vtt")) written().mapIndexed { i, line -> SubtitleCue(i * 20.0, i * 20.0 + 2.0, line) } else emptyList()

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
