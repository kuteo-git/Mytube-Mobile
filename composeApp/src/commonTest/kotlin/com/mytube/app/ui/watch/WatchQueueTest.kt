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

    /**
     * Retry must not buy the write twice.
     *
     * `load()` runs `ensureInCatalogue` every time, and `retry()` is `load()`.
     * The queue used to be a constructor `val`, so the row this sitting had
     * already written still read `inLibrary = false` — and every press of Try
     * again was another `POST /api/videos/external`, which is one full metadata
     * fetch upstream. The charter records what this library has already been
     * blocked for: *"every full metadata request answered with 'Sign in to
     * confirm you're not a bot', taking stream resolution down with it."*
     */
    @Test
    fun `a row written once is not written again when the stream is retried`() = runTest(dispatcher) {
        val videos = FakeVideos(catalogue = mutableSetOf("first"))
        val model = open(
            videos,
            queue = listOf(item("first", inLibrary = true), item("second")),
            streams = FailingStreams,
        )

        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()
        assertTrue(model.state.value is WatchState.Failed, "expected the stream to fail")
        assertEquals(1, videos.ensured.size)

        model.retry()
        advanceUntilIdle()
        model.retry()
        advanceUntilIdle()

        assertEquals(
            listOf("https://www.youtube.com/watch?v=second"),
            videos.ensured,
            "the row was already written; two retries must not write it twice",
        )
    }

    /**
     * A row written under a different id keeps its place in the queue.
     *
     * `ensureInCatalogue` may be handed back an id other than the one it asked
     * about, and `nextId` finds this video in the queue **by id**. Without the
     * queue being rewritten alongside `videoId`, nothing matches — and the rest
     * of a channel sorted by Popular quietly follows the recommendation rail
     * instead of the order somebody chose. Silent, which is what makes it worth
     * a test rather than a comment.
     */
    @Test
    fun `a row written under a new id still knows what follows it`() = runTest(dispatcher) {
        val videos = FakeVideos(
            catalogue = mutableSetOf("first", "third"),
            writesAs = { "$it-in-catalogue" },
        )
        val model = open(
            videos,
            queue = listOf(
                item("first", inLibrary = true),
                item("second"),
                item("third", inLibrary = true),
            ),
        )

        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state is WatchState.Playing, "expected the video to play, was $state")
        assertEquals("second-in-catalogue", state.video.id)
        assertEquals("third", state.nextId, "next must stay inside the queue")
    }

    /**
     * An empty id back is a refusal wearing a success's clothes.
     *
     * The gateway answers 200 with `{"videoId":""}` when it could not resolve
     * the address. Nothing is renamed on the strength of that, and the load
     * fails the way it would have anyway — which is honest: there is genuinely
     * nothing to play.
     */
    @Test
    fun `an empty id back leaves the video alone and fails the load`() = runTest(dispatcher) {
        val videos = FakeVideos(catalogue = mutableSetOf("first"), writesAs = { "" })
        val model = open(videos, queue = listOf(item("first", inLibrary = true), item("second")))

        model.advanceTo("second", fromTheStart = true)
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state is WatchState.Failed, "expected the load to fail, was $state")
        assertEquals(1, videos.ensured.size, "the write was attempted once")
    }

    private fun item(id: String, inLibrary: Boolean = false) = QueueItem(
        id = id,
        sourceUrl = if (inLibrary) "" else "https://www.youtube.com/watch?v=$id",
        inLibrary = inLibrary,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.open(
        videos: FakeVideos,
        queue: List<QueueItem>,
        streams: StreamRepository = FakeStreams,
        videoId: String = "first",
    ): WatchViewModel {
        val model = WatchViewModel(
            videoId = videoId,
            startAtBeginning = true,
            autoPlay = false,
            onFinished = {},
            mediaBaseUrl = "http://mac:8180",
            videos = videos,
            streams = streams,
            narration = FakeNarration,
            preferences = FakePreferences,
            openedFrom = queue,
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

    /**
     * The catalogue, and what the gateway was asked to write into it.
     *
     * [writesAs] is how the gateway names the row it has written. It answers
     * with the id it was asked about today, so the default does that — but the
     * app does not depend on it, and the test for that needs a gateway which
     * answers with something else.
     */
    private class FakeVideos(
        private val catalogue: MutableSet<String>,
        private val writesAs: (String) -> String = { it },
    ) : VideoRepository {
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
            val id = writesAs(sourceUrl.substringAfterLast('='))
            if (id.isNotEmpty()) catalogue += id
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

    /** A gateway that is reachable for the write and not for the stream. */
    private object FailingStreams : StreamRepository {
        override suspend fun stream(videoId: String, maxHeight: Int): Stream =
            error("gateway answered 500 for /api/videos/$videoId/stream")
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
