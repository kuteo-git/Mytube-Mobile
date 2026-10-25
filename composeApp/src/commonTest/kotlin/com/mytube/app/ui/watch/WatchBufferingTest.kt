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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The spinner over the picture, from the press to the first frame.
 *
 * Reported from the sofa: a video opened with a spinner, which went, and then
 * the screen stayed black for a while before the picture came. Measured on the
 * TV emulator with Media3's event log: BUFFERING 0.2s after the press, the
 * first frame 3.2s later — and the spinner gone for all of it. The player said
 * it was buffering while this screen was still resolving the stream, the report
 * was dropped as having nowhere to go, and `Playing` began from a blank state
 * that said nothing was loading. Media3 says nothing more until READY.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchBufferingTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a video still buffering when it opens shows that it is loading`() = runTest(dispatcher) {
        val viewModel = WatchViewModel(
            videoId = "abc",
            startAtBeginning = true,
            autoPlay = true,
            onFinished = {},
            mediaBaseUrl = "http://mac:8180",
            videos = FakeVideos("thumbnails/abc.jpg"),
            streams = FakeStreams,
            storyboards = FakeStoryboards,
            narration = FakeNarration,
            preferences = FakePreferences,
            maxHeight = TV_MAX_HEIGHT,
            playerFactory = FakePlayerFactory(),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is WatchState.Playing && state.playback.isBuffering)
    }

    private class FakePlayerFactory : VideoPlayerFactory {
        val player = FakePlayer()
        override fun create(): VideoPlayer = player
    }

    /** Buffering from the moment it is handed a stream, and nothing more said: a slow first frame. */
    private class FakePlayer : VideoPlayer {
        private val _state = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = _state
        override val rendersSubtitles = false
        override fun load(media: PlayingMedia, startAtSeconds: Double) {
            _state.update { PlaybackState(isBuffering = true) }
        }
        override fun play() {}
        override fun pause() {}
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
            title = "A broadcast",
            channel = Channel(id = "c", name = "CNN", handle = "@cnn", avatarPath = ""),
            durationSeconds = 0,
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

    private object FakeStreams : StreamRepository {
        override suspend fun stream(videoId: String, maxHeight: Int): Stream =
            Stream.Playable(url = "http://mac:8180/api/live/abc/master.m3u8", height = 720, isLive = true)
    }

    private object FakeNarration : NarrationRepository {
        override suspend fun start(videoId: String, fromSeconds: Double) {}
        override suspend fun stop(videoId: String) {}
        override suspend fun state(videoId: String) =
            Narration(NarrationStatus.Idle, done = 0, total = 0, clips = emptyList())
    }

    /**
     * The device's settings, read after the stream is handed over — and on a
     * device that read suspends. The player's first report lands in that gap,
     * which is the whole fault; a fake that answered at once would hide it.
     */
    private object FakePreferences : PreferencesRepository {
        override suspend fun narration(): Boolean { delay(10); return false }
        override suspend fun setNarration(on: Boolean) {}
        override suspend fun autoplay(): Boolean { delay(10); return false }
        override suspend fun setAutoplay(on: Boolean) {}
        override suspend fun subtitleLanguage(): String { delay(10); return "" }
        override suspend fun setSubtitleLanguage(language: String) {}
        override suspend fun voiceLevel(): Float { delay(10); return 1f }
        override suspend fun setVoiceLevel(level: Float) {}
        override suspend fun duckLevel(): Float { delay(10); return 0.2f }
        override suspend fun setDuckLevel(level: Float) {}
        override suspend fun lastVideoId() = ""
        override suspend fun setLastVideoId(id: String) {}
    }
}
