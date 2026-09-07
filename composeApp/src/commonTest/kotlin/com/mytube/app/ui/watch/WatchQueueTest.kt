package com.mytube.app.ui.watch

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.ChannelPage
import com.mytube.app.domain.repository.FeedMix
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.PlaylistPage
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
import kotlin.test.assertTrue

/**
 * Pressing next inside a channel's queue.
 *
 * A channel's uploads come from **YouTube**, so most of the rows on that page
 * have no catalogue entry at all. `ChannelViewModel.openVideo` knows that and
 * writes the row before it navigates — and the queue it hands over goes
 * straight past it: pressing next asked `/api/videos/{id}` for an upstream id
 * the household had never imported, and the watch screen answered
 * *"gateway answered 404"* for a video YouTube was serving perfectly well.
 *
 * That is the same lie the charter records for the live and local tiers, one
 * screen over, and the doc comment on `openVideo` claimed the opposite:
 * *"every one of them takes this same path when it is reached"*. Nothing took
 * it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchQueueTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the next row in a channel's queue is written before it is opened`() = runTest(dispatcher) {
        val videos = FakeVideos(catalogue = mutableSetOf("first"))
        val model = open(videos, queue = listOf(item("first", inLibrary = true), item("second")))

        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state is WatchState.Playing, "expected the video to play, was $state")
        assertEquals("second", state.video.id)
        assertEquals(listOf("https://www.youtube.com/watch?v=second"), videos.ensured)
    }

    @Test
    fun `a row already in the library is not written again`() = runTest(dispatcher) {
        val videos = FakeVideos(catalogue = mutableSetOf("first", "second"))
        val model = open(
            videos,
            queue = listOf(item("first", inLibrary = true), item("second", inLibrary = true)),
        )

        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()

        assertEquals(emptyList(), videos.ensured)
        assertTrue(model.state.value is WatchState.Playing)
    }

    private fun item(id: String, inLibrary: Boolean = false) = QueueItem(
        id = id,
        sourceUrl = if (inLibrary) "" else "https://www.youtube.com/watch?v=$id",
        inLibrary = inLibrary,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.open(
        videos: FakeVideos,
        queue: List<QueueItem>,
    ): WatchViewModel {
        val model = WatchViewModel(
            videoId = "first",
            startAtBeginning = true,
            autoPlay = false,
            onFinished = {},
            mediaBaseUrl = "http://mac:8180",
            videos = videos,
            streams = FakeStreams,
            narration = FakeNarration,
            preferences = FakePreferences,
            queue = queue,
            playerFactory = FakePlayerFactory(),
        )
        advanceUntilIdle()
        return model
    }

    private class FakePlayerFactory : VideoPlayerFactory {
        override fun create(): VideoPlayer = FakePlayer()
    }

    private class FakePlayer : VideoPlayer {
        override val state = MutableStateFlow(PlaybackState()) as StateFlow<PlaybackState>
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

    /** The catalogue, and what the gateway was asked to write into it. */
    private class FakeVideos(private val catalogue: MutableSet<String>) : VideoRepository {
        val ensured = mutableListOf<String>()

        override suspend fun video(id: String): Video {
            // What the gateway does for a video it has no row for, and what the
            // watch screen turned into "gateway answered 404".
            if (id !in catalogue) error("gateway answered 404 for /api/videos/$id")
            return Video(
                id = id,
                title = id,
                channel = Channel(id = "c", name = "Hoài Lâm", handle = "@hl", avatarPath = ""),
                durationSeconds = 100,
                viewCount = 0,
                publishedAt = "",
                thumbnailPath = "thumbnails/$id.jpg",
            )
        }

        override suspend fun ensureExternal(sourceUrl: String): String {
            ensured += sourceUrl
            val id = sourceUrl.substringAfterLast('=')
            catalogue += id
            return id
        }

        override suspend fun feed(topic: String, pageToken: String) = FeedPage(emptyList(), "")
        override suspend fun missed(pageToken: String) = FeedPage(emptyList(), "")
        override suspend fun search(query: String) = emptyList<Video>()
        override suspend fun discover(query: String, limit: Int) = emptyList<ExternalVideo>()
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

    private object FakeStreams : StreamRepository {
        override suspend fun stream(videoId: String, maxHeight: Int): Stream =
            Stream.Playable(url = "http://mac:8180/hls/$videoId/master.m3u8", height = 720)
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
