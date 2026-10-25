package com.mytube.app.ui

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.ChannelPage
import kotlinx.coroutines.delay
import com.mytube.app.domain.repository.SortOption
import com.mytube.app.ui.channel.ChannelState
import com.mytube.app.ui.channel.ChannelViewModel
import com.mytube.app.ui.watch.QueueItem
import com.mytube.app.ui.watch.asQueueItem
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
        var queue = emptyList<QueueItem>()
        model.openVideo(upstream("abc"), listOf(upstream("abc").asQueueItem())) { id, rest ->
            opened = id
            queue = rest
        }
        advance()

        assertEquals(1, videos.ensureCalls)
        // The row is written now, and the queue says so — otherwise the watch
        // screen writes it a second time on arrival, which is one full metadata
        // fetch upstream for a row that is already there.
        assertEquals(
            listOf(
                QueueItem(
                    "written-id",
                    // `written` keeps the address: the row is findable by it if
                    // the id ever has to be resolved again.
                    sourceUrl = "https://www.youtube.com/watch?v=abc",
                    inLibrary = true,
                ),
            ),
            queue,
        )
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

    /**
     * Reported from the television: pick Oldest on a channel and the page stayed
     * a skeleton for good. While a sort is out the grid has no cards, so its
     * "near the end" check is true and asks for the next page — of the *old*
     * order. That answer was written back over the state as it had been when
     * it was asked, `sorting = true` included, after the sorted page had
     * already landed.
     */
    @Test
    fun aNextPageAskedForDuringASortDoesNotUndoTheSort() = runTest(dispatcher) {
        val latest = SortOption("Latest", "")
        val oldest = SortOption("Oldest", "old")
        val videos = FakeVideos(
            sorts = listOf(latest, oldest),
            pages = mapOf(
                ("" to "") to (listOf(upstream("new1")) to "p2"),
                // The old order's continuation, asked with the new order's
                // token beside it — the gateway reads only `pageToken`.
                ("old" to "p2") to (listOf(upstream("new2")) to ""),
                ("old" to "") to (listOf(upstream("old1")) to ""),
            ),
            delays = mapOf(("old" to "p2") to 1_000L, ("old" to "") to 100L),
        )
        val model = ChannelViewModel("chan", videos)
        advance()

        model.selectSort(oldest)
        model.loadMore()
        advance()

        val ready = assertIs<ChannelState.Ready>(model.state.value)
        assertEquals(false, ready.sorting)
        assertEquals(listOf("old1"), ready.videos.map { it.id })
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
        private val sorts: List<SortOption> = emptyList(),
        /** Pages by (sort token, page token), each with its next token; [page] otherwise. */
        private val pages: Map<Pair<String, String>, Pair<List<Video>, String>> = emptyMap(),
        private val delays: Map<Pair<String, String>, Long> = emptyMap(),
    ) : StubVideoRepository() {
        var ensureCalls = 0
            private set

        override suspend fun channelPage(
            channelId: String,
            sortToken: String,
            pageToken: String,
        ): ChannelPage {
            val key = sortToken to pageToken
            delays[key]?.let { delay(it) }
            val (videos, next) = pages[key] ?: (page to "")
            return ChannelPage(
                channel = channel(),
                videoCount = videos.size,
                videos = videos,
                sortOptions = sorts,
                nextPageToken = next,
            )
        }

        override suspend fun ensureExternal(sourceUrl: String): String {
            ensureCalls++
            return ensured
        }

        override suspend fun feed(topic: String, pageToken: String) = throw NotImplementedError()
        override suspend fun video(id: String) = throw NotImplementedError()
        override suspend fun playlist(playlistId: String, pageToken: String) =
            throw NotImplementedError()
        override suspend fun createPlaylist(title: String) = throw NotImplementedError()
        override suspend fun updatePlaylist(
            playlistId: String,
            title: String,
            description: String,
        ) = throw NotImplementedError()
    }
}
