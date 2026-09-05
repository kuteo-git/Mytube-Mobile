package com.mytube.app.ui.watch

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Topic
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
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * What the lock screen is handed.
 *
 * The first test of `WatchViewModel`, and it exists because of a fault nothing
 * else could have caught: the artwork address was built by pasting `/media/`
 * in front of `thumbnailPath`, which is right for a video in the library and
 * wrong for a broadcast — the gateway sends those an absolute address at
 * YouTube. Measured against the running server: the pasted version answered
 * **404** and the thumbnail itself answered 200, so a live video played with an
 * empty square on the lock screen.
 *
 * `imageModel` is the app's answer to exactly this question and every card in
 * every list already uses it. The bug was one call site not asking. That is why
 * the test is here rather than on `imageModel`, which was correct all along:
 * asserting the shared rule would pass while the caller ignoring it stayed
 * broken.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchArtworkTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a broadcast's artwork is the address the gateway sent`() = runTest(dispatcher) {
        // A broadcast's thumbnail is upstream: the catalogue never scanned one,
        // because a stream on air has no file behind it.
        val player = open(thumbnailPath = "https://i.ytimg.com/vi/abc/hq720.jpg")

        assertEquals("https://i.ytimg.com/vi/abc/hq720.jpg", player.loaded?.artworkUrl)
    }

    @Test
    fun `a library video's artwork is still served from the library`() = runTest(dispatcher) {
        val player = open(thumbnailPath = "thumbnails/abc.jpg")

        assertEquals("http://mac:8180/media/thumbnails/abc.jpg", player.loaded?.artworkUrl)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.open(
        thumbnailPath: String,
    ): FakePlayer {
        val factory = FakePlayerFactory()
        WatchViewModel(
            videoId = "abc",
            startAtBeginning = true,
            autoPlay = false,
            onFinished = {},
            mediaBaseUrl = "http://mac:8180",
            videos = FakeVideos(thumbnailPath),
            streams = FakeStreams,
            narration = FakeNarration,
            preferences = FakePreferences,
            playerFactory = factory,
        )
        advanceUntilIdle()
        return factory.player
    }

    private class FakePlayerFactory : VideoPlayerFactory {
        val player = FakePlayer()
        override fun create(): VideoPlayer = player
    }

    private class FakePlayer : VideoPlayer {
        var loaded: PlayingMedia? = null
        override val state = MutableStateFlow(PlaybackState()) as StateFlow<PlaybackState>
        override val rendersSubtitles = false
        override fun load(media: PlayingMedia, startAtSeconds: Double) { loaded = media }
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
