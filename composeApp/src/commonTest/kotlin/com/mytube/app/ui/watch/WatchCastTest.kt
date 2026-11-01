package com.mytube.app.ui.watch

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.PreferencesRepository
import com.mytube.app.ui.cast.Prefs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The watch screen while a speaker plays: it follows the speaker's song, and the
 * phone's paused player must not speak for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchCastTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a speaker gets the rest of the list a video was opened from`() {
        val queue = listOf(item("a"), item("b"), item("c", inLibrary = false), item("d"))
        assertEquals(listOf("b", "d"), castQueueOf("b", queue))
        assertEquals(listOf("x"), castQueueOf("x", queue), "a video not in the list goes alone")
        assertEquals(listOf("d"), castQueueOf("d", queue))
    }

    @Test
    fun `a video opened from the feed takes its up next to the speaker`() = runTest(dispatcher) {
        val model = open(RailVideos(rail = listOf("r1", "r2")))
        assertEquals(listOf("first", "r1", "r2"), model.castQueue())
    }

    /**
     * Following the speaker onto its next song must not write the phone's
     * paused position for the song before: the cast service records the
     * speaker's, and this would put "30 seconds in" over "finished".
     */
    @Test
    fun `following a speaker reports nothing for the paused video here`() = runTest(dispatcher) {
        val videos = RailVideos(rail = emptyList())
        val players = Players()
        val model = open(videos, players)
        players.made.single().state.value = PlaybackState(positionSeconds = 30.0, durationSeconds = 100.0)
        advanceUntilIdle()

        model.playsElsewhere = true
        model.advanceTo("second", fromTheStart = false)
        advanceUntilIdle()

        assertEquals(emptyList(), videos.recorded)
    }

    @Test
    fun `moving on with nothing elsewhere still reports where it was`() = runTest(dispatcher) {
        val videos = RailVideos(rail = emptyList())
        val players = Players()
        val model = open(videos, players)
        players.made.single().state.value = PlaybackState(positionSeconds = 30.0, durationSeconds = 100.0)
        advanceUntilIdle()

        model.advanceTo("second", fromTheStart = false)
        advanceUntilIdle()

        assertEquals(listOf("first"), videos.recorded, "the other half: the guard is not simply 'never report'")
    }

    /**
     * "bật cho watch thì ko đồng nghĩa đã bật cho thuyết minh": the speaker's
     * switch is the phone's preference, so a sitting the speaker hands back
     * narrates as that switch was left — on from where the speaker was, or off.
     */
    @Test
    fun `a sitting handed back narrates as the speaker's switch was left`() = runTest(dispatcher) {
        val prefs = Prefs(narration = false)
        val narration = Starts()
        val model = open(RailVideos(rail = emptyList()), prefs = prefs, narration = narration)
        model.playsElsewhere = true

        prefs.narration = true // switched on at the speaker
        model.playsElsewhere = false
        model.resumeFromSpeaker(200.0)
        advanceUntilIdle()
        assertEquals(listOf(200.0), narration.from, "the speaker narrated; the phone does not")
        assertTrue((model.state.value as WatchState.Playing).narrating)

        model.playsElsewhere = true
        prefs.narration = false // and off again there
        model.playsElsewhere = false
        model.resumeFromSpeaker(300.0)
        advanceUntilIdle()
        assertFalse((model.state.value as WatchState.Playing).narrating, "the speaker stopped narrating; the phone did not")
    }

    /** Every start asked of the server; a pass with nothing to say. */
    private class Starts : NarrationRepository {
        val from = mutableListOf<Double>()
        override suspend fun start(videoId: String, fromSeconds: Double) {
            from += fromSeconds
        }
        override suspend fun stop(videoId: String) {}
        override suspend fun state(videoId: String) =
            Narration(NarrationStatus.Done, done = 0, total = 0, clips = emptyList())
    }

    private fun item(id: String, inLibrary: Boolean = true) = QueueItem(id = id, sourceUrl = "", inLibrary = inLibrary)

    private fun TestScope.open(
        videos: RailVideos,
        players: Players = Players(),
        prefs: PreferencesRepository = InstantPreferences,
        narration: NarrationRepository = IdleNarration,
    ): WatchViewModel {
        val model = WatchViewModel(
            videoId = "first",
            startAtBeginning = true,
            autoPlay = false,
            onFinished = {},
            mediaBaseUrl = "http://mac:8180",
            videos = videos,
            streams = Streams,
            storyboards = NoStoryboards,
            narration = narration,
            preferences = prefs,
            openedFrom = emptyList(),
            maxHeight = PHONE_MAX_HEIGHT,
            playerFactory = players,
        )
        advanceUntilIdle()
        return model
    }

    private class RailVideos(private val rail: List<String>) : StubVideoRepository() {
        val recorded = mutableListOf<String>()

        override suspend fun video(id: String) = video(id, inLibrary = true)

        override suspend fun upNext(videoId: String, channelId: String) =
            rail.map { video(it, inLibrary = true) } + video("upstream", inLibrary = false)

        override suspend fun recordProgress(videoId: String, positionSeconds: Double, watchedFraction: Double) {
            recorded += videoId
        }

        private fun video(id: String, inLibrary: Boolean) = Video(
            id = id,
            title = id,
            channel = Channel(id = "c", name = "Hoài Lâm", handle = "@hl", avatarPath = ""),
            durationSeconds = 100,
            viewCount = 0,
            publishedAt = "",
            thumbnailPath = "thumbnails/$id.jpg",
            inLibrary = inLibrary,
            subtitles = listOf(SubtitleTrack("en", "English", "/media/$id/en.vtt", generated = false)),
        )
    }

    private object Streams : StreamRepository {
        override suspend fun stream(videoId: String, maxHeight: Int): Stream =
            Stream.Playable(url = "http://mac:8180/hls/$videoId/master.m3u8", height = 720)
    }

    private class Players : VideoPlayerFactory {
        val made = mutableListOf<Player>()
        override fun create(): VideoPlayer = Player().also { made += it }
    }

    private class Player : VideoPlayer {
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
}
