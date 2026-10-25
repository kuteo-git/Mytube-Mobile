package com.mytube.app.ui.watch

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.StoryboardState
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.ChannelPage
import com.mytube.app.domain.repository.FeedMix
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.repository.PlaylistPage
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.PreferencesRepository
import com.mytube.app.domain.repository.StoryboardRepository
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * What the watch screen asks of the server and the player on a television.
 *
 * Two additions for the TV, each a single line in `WatchViewModel` and each one
 * that is wrong silently: a ceiling of 720 on the television reads as the app
 * being blurry rather than as a missing argument, and a Home button that
 * toggled would start a paused video playing behind the launcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchTelevisionTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // --- the ceiling -----------------------------------------------------------

    /** A Google TV box draws 1920x1080: the screen, not a saving. */
    @Test
    fun `a television asks the server for 1080p`() = runTest(dispatcher) {
        val streams = RecordingStreams()
        open(streams = streams, maxHeight = TV_MAX_HEIGHT)

        assertEquals(listOf("abc" to 1080), streams.asked)
    }

    @Test
    fun `a phone asks the server for 720p`() = runTest(dispatcher) {
        val streams = RecordingStreams()
        open(streams = streams, maxHeight = PHONE_MAX_HEIGHT)

        assertEquals(listOf("abc" to 720), streams.asked)
    }

    /** The ceiling belongs to the device, not to the first load: asking again keeps it. */
    @Test
    fun `retry asks with the same ceiling`() = runTest(dispatcher) {
        val streams = RecordingStreams()
        val (model, _) = open(streams = streams, maxHeight = TV_MAX_HEIGHT)

        model.retry()
        advanceUntilIdle()

        assertEquals(listOf("abc" to 1080, "abc" to 1080), streams.asked)
    }

    // --- pause -----------------------------------------------------------------

    @Test
    fun `pause stops a playing video`() = runTest(dispatcher) {
        val (model, player) = open(autoPlay = true)
        assertTrue(player.state.value.isPlaying, "autoplay should have started it")

        model.pause()

        assertEquals(1, player.pauses)
        assertFalse(player.state.value.isPlaying)
    }

    /**
     * The television's Home button means "pause", every time. Pressed twice,
     * or on a video already paused, it must never be the toggle that resumes.
     */
    @Test
    fun `pause never resumes a paused video`() = runTest(dispatcher) {
        val (model, player) = open(autoPlay = true)
        val playsBefore = player.plays

        model.pause()
        advanceUntilIdle()
        model.pause()
        advanceUntilIdle()

        assertEquals(playsBefore, player.plays, "pause must not call play")
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun `pause on a video that never started leaves it paused`() = runTest(dispatcher) {
        val (model, player) = open(autoPlay = false)
        assertFalse(player.state.value.isPlaying)

        model.pause()
        advanceUntilIdle()

        assertEquals(0, player.plays)
        assertFalse(player.state.value.isPlaying)
    }

    /**
     * Why pause is its own act: the toggle beside it, pressed on the same
     * paused video, resumes it. This is the behaviour the Home button must not
     * have, asserted so the two cannot quietly become one.
     */
    @Test
    fun `the play-pause toggle on a paused video resumes it, which pause does not`() = runTest(dispatcher) {
        val (model, player) = open(autoPlay = true)
        model.pause()
        advanceUntilIdle()
        val playsBefore = player.plays

        model.playPause()
        advanceUntilIdle()

        assertEquals(playsBefore + 1, player.plays)
        assertTrue(player.state.value.isPlaying)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.open(
        streams: StreamRepository = RecordingStreams(),
        maxHeight: Int = TV_MAX_HEIGHT,
        autoPlay: Boolean = false,
    ): Pair<WatchViewModel, FakePlayer> {
        val factory = FakePlayerFactory()
        val model = WatchViewModel(
            videoId = "abc",
            startAtBeginning = true,
            autoPlay = autoPlay,
            onFinished = {},
            mediaBaseUrl = "http://mac:8180",
            videos = FakeVideos(thumbnailPath = "thumbnails/abc.jpg"),
            streams = streams,
            storyboards = FakeStoryboards,
            narration = FakeNarration,
            preferences = FakePreferences,
            maxHeight = maxHeight,
            playerFactory = factory,
        )
        advanceUntilIdle()
        return model to factory.player
    }

    /** What the server was asked for, as (video, ceiling). */
    private class RecordingStreams : StreamRepository {
        val asked = mutableListOf<Pair<String, Int>>()
        override suspend fun stream(videoId: String, maxHeight: Int): Stream {
            asked += videoId to maxHeight
            return Stream.Playable(url = "http://mac:8180/api/videos/abc/hls/master.m3u8", height = maxHeight, isLive = false)
        }
    }

    private class FakePlayerFactory : VideoPlayerFactory {
        val player = FakePlayer()
        override fun create(): VideoPlayer = player
    }

    /** A player that does what it is told, and counts how often it was told. */
    private class FakePlayer : VideoPlayer {
        var plays = 0
            private set
        var pauses = 0
            private set
        private val _state = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = _state
        override val rendersSubtitles = false
        override fun load(media: PlayingMedia, startAtSeconds: Double) {
            _state.update { it.copy(durationSeconds = 600.0, positionSeconds = startAtSeconds) }
        }
        override fun play() {
            plays++
            _state.update { it.copy(isPlaying = true) }
        }
        override fun pause() {
            pauses++
            _state.update { it.copy(isPlaying = false) }
        }
        override fun seekTo(seconds: Double) {}
        override fun narrate(clips: List<NarrationClip>) {}
        override fun setNarrationLevels(voice: Float, duck: Float) {}
        override fun showSubtitles(language: String) {}
        override fun stop() {}
        override fun release() {}
    }

    private class FakeVideos(private val thumbnailPath: String) : VideoRepository {
        override suspend fun video(id: String) = Video(
            id = id,
            title = "A film",
            channel = Channel(id = "c", name = "CNN", handle = "@cnn", avatarPath = ""),
            durationSeconds = 600,
            viewCount = 0,
            publishedAt = "",
            thumbnailPath = thumbnailPath,
        )

        override suspend fun feed(topic: String, pageToken: String) = FeedPage(emptyList(), "")
        override suspend fun missed(pageToken: String) = FeedPage(emptyList(), "")
        override suspend fun search(query: String) = emptyList<Video>()
        override suspend fun discover(query: String, limit: Int) = emptyList<ExternalVideo>()
        override suspend fun ensureExternal(sourceUrl: String) = ""
        override suspend fun resolveChannel(query: String) = ""
        override suspend fun topics() = emptyList<Topic>()
        override suspend fun live() = emptyList<Video>()
        override suspend fun history(limit: Int) = emptyList<Video>()
        override suspend fun saved() = emptyList<Video>()
        override suspend fun feedMix() = FeedMix(0, 0, 0, 0)
        override suspend fun saveFeedMix(mix: FeedMix) {}
        override suspend fun subscriptions() = emptyList<Channel>()
        override suspend fun subtitleCues(url: String) = emptyList<SubtitleCue>()
        override suspend fun comments(videoId: String) = emptyList<Comment>()
        override suspend fun importComments(videoId: String) {}
        override suspend fun refreshMetadata(videoId: String) = Unit
        override suspend fun upNext(videoId: String, channelId: String) = emptyList<Video>()
        override suspend fun recordProgress(
            videoId: String,
            positionSeconds: Double,
            watchedFraction: Double,
        ) {}
        override suspend fun setReaction(videoId: String, reaction: Reaction) {}
        override suspend fun setSaved(videoId: String, saved: Boolean) {}
        override suspend fun setSubscribed(channelId: String, subscribed: Boolean) {}
        override suspend fun setNotInterested(videoId: String) {}
        override suspend fun playlists(videoId: String) = emptyList<Playlist>()
        override suspend fun playlist(playlistId: String, pageToken: String) =
            PlaylistPage(emptyPlaylist(playlistId), emptyList(), "")
        override suspend fun createPlaylist(title: String) = emptyPlaylist("")
        override suspend fun updatePlaylist(
            playlistId: String,
            title: String,
            description: String,
        ) = emptyPlaylist(playlistId)
        override suspend fun deletePlaylist(playlistId: String) {}
        override suspend fun addToPlaylist(playlistId: String, videoId: String) {}
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) {}

        override suspend fun channelPage(
            channelId: String,
            sortToken: String,
            pageToken: String,
        ) = ChannelPage(
            channel = Channel(id = channelId, name = "", handle = "", avatarPath = ""),
            videoCount = 0,
            videos = emptyList(),
            sortOptions = emptyList(),
            nextPageToken = "",
        )

        private fun emptyPlaylist(id: String) = Playlist(
            id = id,
            title = "",
            description = "",
            itemCount = 0,
            containsVideo = false,
            thumbnailPaths = emptyList(),
        )
    }

    /**
     * No preview, which is what most of this library answers.
     *
     * Neither of these tests is about storyboards, so the honest stub is the
     * answer a video with no ladder really gets — a test that accidentally
     * reaches this takes the path a Short takes rather than one nothing takes.
     */
    private object FakeStoryboards : StoryboardRepository {
        override suspend fun storyboard(videoId: String) = StoryboardState.None
    }

    private object FakeNarration : NarrationRepository {
        override suspend fun start(videoId: String, fromSeconds: Double) {}
        override suspend fun stop(videoId: String) {}
        override suspend fun state(videoId: String) =
            Narration(NarrationStatus.Idle, done = 0, total = 0, clips = emptyList())
    }

    private object FakePreferences : PreferencesRepository {
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
}
