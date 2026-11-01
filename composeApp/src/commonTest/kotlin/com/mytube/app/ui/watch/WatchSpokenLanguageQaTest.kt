package com.mytube.app.ui.watch

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.SpokenLanguage
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.ui.cast.Prefs
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** QA cases for "a Vietnamese-spoken video opens with narration off". */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchSpokenLanguageQaTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // A1
    @Test
    fun `with the switch off the language is never asked`() = runTest(dispatcher) {
        val videos = Spoken(mapOf("first" to SpokenLanguage.Other))
        val passes = RecordedPasses()
        val model = open(videos, passes, Prefs(narration = false))
        assertFalse(playing(model).narrating)
        assertEquals(emptyList(), videos.asked)
        assertEquals(emptyList(), passes.started)
    }

    // A2
    @Test
    fun `unknown follows the switch`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Unknown)), passes, Prefs(narration = true))
        assertTrue(playing(model).narrating)
        assertEquals(listOf("first"), passes.started)
    }

    // A3
    @Test
    fun `a failed ask narrates as the switch says`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val model = open(Spoken(emptyMap(), fails = true), passes, Prefs(narration = true))
        assertTrue(playing(model).narrating)
        assertEquals(listOf("first"), passes.started)
    }

    // A4
    @Test
    fun `pressing on for a Vietnamese video with the switch on narrates it`() = runTest(dispatcher) {
        val prefs = Prefs(narration = true)
        val passes = RecordedPasses()
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Vietnamese)), passes, prefs)
        assertFalse(playing(model).narrating)
        model.toggleNarration()
        advanceUntilIdle()
        assertTrue(playing(model).narrating)
        assertTrue(prefs.narration)
        assertEquals(listOf("first"), passes.started)
    }

    // A5
    @Test
    fun `a press on while a Vietnamese answer is pending keeps narrating`() = runTest(dispatcher) {
        val answer = CompletableDeferred<SpokenLanguage>()
        val passes = RecordedPasses()
        val model = open(Spoken(emptyMap(), pending = mapOf("first" to answer)), passes, Prefs(narration = true))
        model.toggleNarration()
        advanceUntilIdle()
        answer.complete(SpokenLanguage.Vietnamese)
        advanceUntilIdle()
        assertTrue(playing(model).narrating)
        assertEquals(listOf("first"), passes.started, "one pass, from the press")
    }

    // A6a
    @Test
    fun `advancing from English to Vietnamese stops narrating and asks no pass`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val videos = Spoken(mapOf("first" to SpokenLanguage.Other, "second" to SpokenLanguage.Vietnamese))
        val model = open(videos, passes, Prefs(narration = true))
        assertTrue(playing(model).narrating)
        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()
        assertFalse(playing(model).narrating)
        assertEquals(listOf("first"), passes.started)
        assertEquals(listOf("first", "second"), videos.asked)
    }

    // A6b
    @Test
    fun `advancing from Vietnamese to English narrates the English one`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val videos = Spoken(mapOf("first" to SpokenLanguage.Vietnamese, "second" to SpokenLanguage.Other))
        val model = open(videos, passes, Prefs(narration = true))
        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()
        assertTrue(playing(model).narrating)
        assertEquals(listOf("second"), passes.started)
    }

    // A7
    @Test
    fun `Previous back to a Vietnamese video pressed on opens plain`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val videos = Spoken(mapOf("first" to SpokenLanguage.Vietnamese, "second" to SpokenLanguage.Other))
        val model = open(videos, passes, Prefs(narration = true))
        model.toggleNarration() // on, for this sitting
        advanceUntilIdle()
        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()
        model.advanceTo("first", fromTheStart = false)
        advanceUntilIdle()
        assertFalse(playing(model).narrating)
    }

    // A12
    @Test
    fun `a slow answer for the video left does not decide the next one`() = runTest(dispatcher) {
        val slow = CompletableDeferred<SpokenLanguage>()
        val passes = RecordedPasses()
        val videos = Spoken(mapOf("second" to SpokenLanguage.Vietnamese), pending = mapOf("first" to slow))
        val model = open(videos, passes, Prefs(narration = true))
        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()
        slow.complete(SpokenLanguage.Other)
        advanceUntilIdle()
        assertEquals("second", playing(model).video.id)
        assertFalse(playing(model).narrating)
        assertEquals(emptyList(), passes.started)
    }

    // A13
    @Test
    fun `a press during a pending answer does not carry to the next video`() = runTest(dispatcher) {
        val slow = CompletableDeferred<SpokenLanguage>()
        val passes = RecordedPasses()
        val videos = Spoken(mapOf("second" to SpokenLanguage.Other), pending = mapOf("first" to slow))
        val model = open(videos, passes, Prefs(narration = true))
        model.toggleNarration() // on
        model.toggleNarration() // off: switch now off
        advanceUntilIdle()
        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()
        assertFalse(playing(model).narrating, "switch was left off")
    }

    // A8a
    @Test
    fun `resuming a Vietnamese video from a speaker stays plain`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Vietnamese)), passes, Prefs(narration = true))
        model.playsElsewhere = true
        model.playsElsewhere = false
        model.resumeFromSpeaker(120.0)
        advanceUntilIdle()
        assertFalse(playing(model).narrating)
        assertEquals(emptyList(), passes.started)
    }

    // A8b
    @Test
    fun `resuming an English video from a speaker narrates from there`() = runTest(dispatcher) {
        val prefs = Prefs(narration = false)
        val passes = RecordedPasses()
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Other)), passes, prefs)
        model.playsElsewhere = true
        prefs.narration = true
        model.playsElsewhere = false
        model.resumeFromSpeaker(120.0)
        advanceUntilIdle()
        assertTrue(playing(model).narrating)
        assertEquals(listOf(120.0), passes.from)
    }

    // A9: the phone's press on a Vietnamese video, then a round trip via a speaker.
    @Test
    fun `a Vietnamese video pressed on here still narrates after a speaker hands it back`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Vietnamese)), passes, Prefs(narration = true))
        model.toggleNarration() // on, by hand, for this Vietnamese video
        advanceUntilIdle()
        assertTrue(playing(model).narrating)
        model.handOverToSpeaker()
        model.playsElsewhere = true
        model.playsElsewhere = false
        model.resumeFromSpeaker(150.0)
        advanceUntilIdle()
        assertTrue(playing(model).narrating, "the press on this video was undone by the hand-back")
    }

    // A10
    @Test
    fun `a video opened while a speaker plays asks nothing and narrates nothing`() = runTest(dispatcher) {
        val videos = Spoken(mapOf("first" to SpokenLanguage.Other))
        val passes = RecordedPasses()
        val model = open(videos, passes, Prefs(narration = true), elsewhere = true)
        assertFalse(playing(model).narrating)
        assertEquals(emptyList(), passes.started)
        assertEquals(emptyList(), videos.asked)
    }

    // A11
    @Test
    fun `a broadcast asks nothing and narrates nothing`() = runTest(dispatcher) {
        val videos = Spoken(mapOf("first" to SpokenLanguage.Other))
        val passes = RecordedPasses()
        val model = open(videos, passes, Prefs(narration = true), streams = LiveStreams)
        assertTrue(playing(model).isLive)
        assertFalse(playing(model).narrating)
        assertEquals(emptyList(), passes.started)
        assertEquals(emptyList(), videos.asked)
    }

    private fun playing(model: WatchViewModel) = model.state.value as WatchState.Playing

    private fun TestScope.open(
        videos: Spoken,
        passes: RecordedPasses,
        prefs: Prefs,
        elsewhere: Boolean = false,
        streams: StreamRepository = PlayableStreams,
    ): WatchViewModel {
        val model = WatchViewModel(
            videoId = "first",
            startAtBeginning = true,
            autoPlay = false,
            onFinished = {},
            mediaBaseUrl = "http://mac:8180",
            videos = videos,
            streams = streams,
            storyboards = NoStoryboards,
            narration = passes,
            preferences = prefs,
            openedFrom = emptyList(),
            maxHeight = PHONE_MAX_HEIGHT,
            playerFactory = SilentPlayers(),
        )
        model.playsElsewhere = elsewhere
        advanceUntilIdle()
        return model
    }

    private object LiveStreams : StreamRepository {
        override suspend fun stream(videoId: String, maxHeight: Int): Stream =
            Stream.Playable(url = "http://mac:8180/live/$videoId.m3u8", height = 720, isLive = true)
    }

    private class Spoken(
        private val answers: Map<String, SpokenLanguage>,
        private val pending: Map<String, CompletableDeferred<SpokenLanguage>> = emptyMap(),
        private val fails: Boolean = false,
    ) : StubVideoRepository() {
        val asked = mutableListOf<String>()

        override suspend fun video(id: String) = Video(
            id = id, title = id,
            channel = Channel(id = "c", name = "Kênh", handle = "@c", avatarPath = ""),
            durationSeconds = 100, viewCount = 0, publishedAt = "", thumbnailPath = "",
            inLibrary = true,
            subtitles = listOf(SubtitleTrack("vi", "Tiếng Việt", "/media/$id/vi.vtt", generated = false)),
        )

        override suspend fun upNext(videoId: String, channelId: String) = emptyList<Video>()

        override suspend fun spokenLanguage(videoId: String): SpokenLanguage {
            asked += videoId
            if (fails) error("gateway down")
            pending[videoId]?.let { return it.await() }
            return answers[videoId] ?: SpokenLanguage.Unknown
        }
    }
}
