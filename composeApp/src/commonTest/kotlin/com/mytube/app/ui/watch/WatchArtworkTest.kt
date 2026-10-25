package com.mytube.app.ui.watch

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.ChannelPage
import com.mytube.app.domain.repository.PlaylistPage
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
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
            storyboards = NoStoryboards,
            narration = IdleNarration,
            preferences = InstantPreferences,
            maxHeight = PHONE_MAX_HEIGHT,
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

    private class FakeVideos(private val thumbnailPath: String) : StubVideoRepository() {
        override suspend fun video(id: String) = Video(
            id = id,
            title = "A broadcast",
            channel = Channel(id = "c", name = "CNN", handle = "@cnn", avatarPath = ""),
            durationSeconds = 0,
            viewCount = 0,
            publishedAt = "",
            thumbnailPath = thumbnailPath,
        )

        override suspend fun playlist(playlistId: String, pageToken: String) =
            PlaylistPage(emptyPlaylist(playlistId), emptyList(), "")
        override suspend fun createPlaylist(title: String) = emptyPlaylist("")
        override suspend fun updatePlaylist(
            playlistId: String,
            title: String,
            description: String,
        ) = emptyPlaylist(playlistId)

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

}
