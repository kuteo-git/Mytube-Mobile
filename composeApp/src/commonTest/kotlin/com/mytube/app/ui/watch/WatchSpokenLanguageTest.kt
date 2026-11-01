package com.mytube.app.ui.watch

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.SpokenLanguage
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.model.narratesOnOpen
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
 * "auto tắt thuyết minh trên tiếng việt và bật nó trên tiếng anh": a video
 * opens narrated as the switch says, unless it speaks Vietnamese — its
 * narration would read its Vietnamese captions over its own voice.
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
        val model = open(SpokenAs(SpokenLanguage.Vietnamese), passes, Prefs(narration = true))
        assertFalse(playing(model).narrating)
        assertEquals(emptyList(), passes.started, "a pass asked for a Vietnamese video nobody asked to narrate")
    }

    @Test
    fun `an English video opens narrated when the switch is on`() = runTest(dispatcher) {
        val passes = RecordedPasses()
        val model = open(SpokenAs(SpokenLanguage.Other), passes, Prefs(narration = true))
        assertTrue(playing(model).narrating)
        assertEquals(listOf("first"), passes.started)
    }

    @Test
    fun `pressing the switch on a Vietnamese video narrates it and remembers it`() = runTest(dispatcher) {
        val prefs = Prefs(narration = false)
        val model = open(SpokenAs(SpokenLanguage.Vietnamese), RecordedPasses(), prefs)
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
        val model = open(SpokenAs(answer), RecordedPasses(), prefs)
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
        val model = open(SpokenAs(SpokenLanguage.Other), RecordedPasses(), prefs)
        model.toggleNarration() // off
        model.toggleNarration() // on again: the switch is on
        advanceUntilIdle()
        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()
        model.advanceTo("first", fromTheStart = true)
        advanceUntilIdle()
        assertTrue(playing(model).narrating, "came back to with the switch on, and opened plain")
    }

    private fun playing(model: WatchViewModel) = model.state.value as WatchState.Playing

    private fun TestScope.open(videos: SpokenAs, passes: RecordedPasses, prefs: Prefs): WatchViewModel {
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
            preferences = prefs,
            openedFrom = emptyList(),
            maxHeight = PHONE_MAX_HEIGHT,
            playerFactory = SilentPlayers(),
        )
        advanceUntilIdle()
        return model
    }

    private class SpokenAs(private val answer: CompletableDeferred<SpokenLanguage>) : StubVideoRepository() {
        constructor(language: SpokenLanguage) : this(CompletableDeferred(language))

        override suspend fun video(id: String) = Video(
            id = id, title = id,
            channel = Channel(id = "c", name = "Kênh", handle = "@c", avatarPath = ""),
            durationSeconds = 100, viewCount = 0, publishedAt = "", thumbnailPath = "",
            inLibrary = true,
            subtitles = listOf(SubtitleTrack("vi", "Tiếng Việt", "/media/$id/vi.vtt", generated = false)),
        )

        override suspend fun upNext(videoId: String, channelId: String) = emptyList<Video>()

        override suspend fun spokenLanguage(videoId: String) = answer.await()
    }
}
