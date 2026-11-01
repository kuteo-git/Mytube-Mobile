package com.mytube.app.ui.watch

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.SpokenLanguage
import com.mytube.app.domain.model.narratesOnOpen
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

/**
 * A video opens narrated as the switch says, unless it speaks Vietnamese — its
 * narration would read its Vietnamese captions over its own voice. Pressing
 * the switch narrates any video and is remembered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchSpokenLanguageTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a video opens narrated unless it speaks Vietnamese`() {
        assertFalse(narratesOnOpen(preferred = true, SpokenLanguage.Vietnamese))
        assertTrue(narratesOnOpen(preferred = true, SpokenLanguage.Other))
        assertTrue(narratesOnOpen(preferred = true, SpokenLanguage.Unknown), "not known: as the switch says")
        assertFalse(narratesOnOpen(preferred = false, SpokenLanguage.Other))
    }

    @Test
    fun `a Vietnamese video opens with narration off though the switch is on`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Vietnamese)), passes, Prefs(narration = true))
        assertFalse(playing(model).narrating)
        assertEquals(emptyList(), passes.started, "a pass asked for a Vietnamese video nobody asked to narrate")
    }

    @Test
    fun `an English video opens narrated when the switch is on`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Other, "second" to SpokenLanguage.Other)), passes, Prefs(narration = true))
        assertTrue(playing(model).narrating)
        assertEquals(listOf("first"), passes.started)
    }

    @Test
    fun `pressing the switch on a Vietnamese video narrates it and remembers it`() = runTest(dispatcher) {
        val prefs = Prefs(narration = false)
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Vietnamese)), RecordedPasses(), prefs)
        model.toggleNarration()
        advanceUntilIdle()
        assertTrue(playing(model).narrating)
        assertTrue(prefs.narration)
    }

    /** A press made while the language is still being asked wins over the answer. */
    @Test
    fun `a press before the answer is not undone by it`() = runTest(dispatcher) {
        val answer = CompletableDeferred<SpokenLanguage>()
        val prefs = Prefs(narration = true)
        val model = open(Spoken(emptyMap(), pending = mapOf("first" to answer)), RecordedPasses(), prefs)
        model.toggleNarration() // on
        model.toggleNarration() // and off again
        advanceUntilIdle()
        answer.complete(SpokenLanguage.Other)
        advanceUntilIdle()
        assertFalse(playing(model).narrating, "the answer turned narration back on over the press")
    }

    /** A press counts for that sitting of the video, not for the next time it opens. */
    @Test
    fun `a video come back to opens as the switch says`() = runTest(dispatcher) {
        val prefs = Prefs(narration = true)
        val model = open(Spoken(mapOf("first" to SpokenLanguage.Other, "second" to SpokenLanguage.Other)), RecordedPasses(), prefs)
        model.toggleNarration() // off
        model.toggleNarration() // on again: the switch is on
        advanceUntilIdle()
        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()
        model.advanceTo("first", fromTheStart = true)
        advanceUntilIdle()
        assertTrue(playing(model).narrating, "came back to with the switch on, and opened plain")
    }

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

    // A3
    @Test
    fun `a failed ask narrates as the switch says`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val model = open(Spoken(emptyMap(), fails = true), passes, Prefs(narration = true))
        assertTrue(playing(model).narrating)
        assertEquals(listOf("first"), passes.started)
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

    /** A press while a hand-back's answer is on its way wins over the answer too. */
    @Test
    fun `a press during a hand-back's ask is not undone by its answer`() = runTest(dispatcher) {
        val answer = CompletableDeferred<SpokenLanguage>()
        val model = open(Spoken(emptyMap(), pending = mapOf("first" to answer)), RecordedPasses(), Prefs(narration = true))
        model.resumeFromSpeaker(100.0)
        advanceUntilIdle()
        model.toggleNarration() // on, while the answer is on its way
        answer.complete(SpokenLanguage.Vietnamese)
        advanceUntilIdle()
        assertTrue(playing(model).narrating, "the hand-back's answer undid the press")
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
