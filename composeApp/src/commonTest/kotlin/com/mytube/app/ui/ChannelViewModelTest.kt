package com.mytube.app.ui

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.ChannelPage
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.channel.ChannelState
import com.mytube.app.ui.channel.ChannelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The channel page, and the one thing about it that is not like any other list
 * in this app: **its rows come from YouTube, not from the catalogue.**
 *
 * Reported from the phone: search, open a channel the household had never
 * imported, press a video — *"gateway answered 404 for
 * /api/videos/bnNMULP-Ftc"*. The video was fine; there was no row to ask about.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aRowWithNoCatalogueEntryIsWrittenBeforeItIsOpened() = runTest(dispatcher) {
        val videos = FakeVideos(page = listOf(upstream("abc")))
        val model = ChannelViewModel("chan", videos)
        advance()

        var opened = ""
        model.openVideo(upstream("abc"), listOf("abc")) { id, _ -> opened = id }
        advance()

        assertEquals(1, videos.ensureCalls)
        // The id the *server* answered with, not the one that was pressed. They
        // are usually the same and the difference is not this side's to assume.
        assertEquals("written-id", opened)
    }

    /** A row already on the disk skips the round trip it does not need. */
    @Test
    fun aRowAlreadyInTheLibraryOpensStraightAway() = runTest(dispatcher) {
        val videos = FakeVideos(page = listOf(upstream("abc")))
        val model = ChannelViewModel("chan", videos)
        advance()

        var opened = ""
        model.openVideo(upstream("abc").copy(inLibrary = true), emptyList()) { id, _ ->
            opened = id
        }
        advance()

        assertEquals(0, videos.ensureCalls)
        assertEquals("abc", opened)
    }

    /**
     * An empty id is a refusal wearing a success's clothes.
     *
     * The gateway answers 200 with `{"videoId":""}` when it could not resolve
     * the address, and opening the watch screen on an empty id opens it on
     * nothing — which is the same 404 in a different costume.
     */
    @Test
    fun anEmptyIdBackIsAFailureAndNavigatesNowhere() = runTest(dispatcher) {
        val videos = FakeVideos(page = listOf(upstream("abc")), ensured = "")
        val model = ChannelViewModel("chan", videos)
        advance()

        var opened = false
        model.openVideo(upstream("abc"), emptyList()) { _, _ -> opened = true }
        advance()

        assertEquals(false, opened)
        assertEquals(true, model.openFailed.value)
        assertEquals("", model.opening.value)
    }

    /** Writing the row is a round trip to YouTube; a second tap must not start another. */
    @Test
    fun aSecondPressWhileOneIsInFlightIsIgnored() = runTest(dispatcher) {
        val videos = FakeVideos(page = listOf(upstream("abc")))
        val model = ChannelViewModel("chan", videos)
        advance()

        model.openVideo(upstream("abc"), emptyList()) { _, _ -> }
        model.openVideo(upstream("def"), emptyList()) { _, _ -> }
        advance()

        assertEquals(1, videos.ensureCalls)
    }

    /** The page still loads, so the failure above is about opening and nothing else. */
    @Test
    fun thePageLoads() = runTest(dispatcher) {
        val model = ChannelViewModel("chan", FakeVideos(page = listOf(upstream("abc"))))
        advance()

        val state = assertIs<ChannelState.Ready>(model.state.value)
        assertEquals(listOf("abc"), state.videos.map { it.id })
    }

    private suspend fun kotlinx.coroutines.test.TestScope.advance() =
        testScheduler.advanceUntilIdle()

    private fun channel() = Channel(
        id = "chan",
        name = "A channel",
        handle = "@chan",
        avatarPath = "",
        subscriberCount = 0,
        subscribed = false,
    )

    /** A row as the channel page gets it: no catalogue entry, and an address. */
    private fun upstream(id: String) = Video(
        id = id,
        title = id,
        channel = channel(),
        durationSeconds = 1,
        viewCount = 0,
        publishedAt = "",
        thumbnailPath = "https://i.ytimg.com/vi/$id/hq720.jpg",
        sourceUrl = "https://www.youtube.com/watch?v=$id",
        inLibrary = false,
    )

    private inner class FakeVideos(
        private val page: List<Video> = emptyList(),
        private val ensured: String = "written-id",
    ) : VideoRepository {
        var ensureCalls = 0
            private set

        override suspend fun channelPage(
            channelId: String,
            sortToken: String,
            pageToken: String,
        ) = ChannelPage(
            channel = channel(),
            videoCount = page.size,
            videos = page,
            sortOptions = emptyList(),
            nextPageToken = "",
        )

        override suspend fun ensureExternal(sourceUrl: String): String {
            ensureCalls++
            return ensured
        }

        override suspend fun feed(topic: String, pageToken: String) = throw NotImplementedError()
        override suspend fun video(id: String) = throw NotImplementedError()
        override suspend fun search(query: String) = emptyList<Video>()
        override suspend fun discover(query: String, limit: Int) = emptyList<ExternalVideo>()
        override suspend fun resolveChannel(query: String) = ""
        override suspend fun topics() = emptyList<com.mytube.app.domain.model.Topic>()
        override suspend fun live() = emptyList<Video>()
        override suspend fun missed(pageToken: String) =
            com.mytube.app.domain.repository.FeedPage(emptyList(), "")
        override suspend fun history(limit: Int) = emptyList<Video>()
        override suspend fun saved() = emptyList<Video>()
        override suspend fun feedMix() = throw NotImplementedError()
        override suspend fun saveFeedMix(mix: com.mytube.app.domain.repository.FeedMix) = Unit
        override suspend fun subscriptions() = emptyList<Channel>()
        override suspend fun upNext(videoId: String, channelId: String) = emptyList<Video>()
        override suspend fun recordProgress(
            videoId: String,
            positionSeconds: Double,
            watchedFraction: Double,
        ) = Unit
        override suspend fun setReaction(
            videoId: String,
            reaction: com.mytube.app.domain.model.Reaction,
        ) = Unit
        override suspend fun setSaved(videoId: String, saved: Boolean) = Unit
        override suspend fun setSubscribed(channelId: String, subscribed: Boolean) = Unit
        override suspend fun setNotInterested(videoId: String) = Unit
        override suspend fun subtitleCues(url: String) = emptyList<SubtitleCue>()
        override suspend fun comments(videoId: String) =
            emptyList<com.mytube.app.domain.model.Comment>()
        override suspend fun importComments(videoId: String) = Unit
        override suspend fun playlists(videoId: String) =
            emptyList<com.mytube.app.domain.model.Playlist>()
        override suspend fun playlist(playlistId: String, pageToken: String) =
            throw NotImplementedError()
        override suspend fun createPlaylist(title: String) = throw NotImplementedError()
        override suspend fun updatePlaylist(
            playlistId: String,
            title: String,
            description: String,
        ) = throw NotImplementedError()
        override suspend fun deletePlaylist(playlistId: String) = Unit
        override suspend fun addToPlaylist(playlistId: String, videoId: String) = Unit
        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) = Unit
    }
}
