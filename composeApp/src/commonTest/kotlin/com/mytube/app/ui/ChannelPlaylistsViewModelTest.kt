package com.mytube.app.ui

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.ChannelPlaylist
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.ChannelPlaylistsPage
import com.mytube.app.domain.repository.PlaylistVideosPage
import com.mytube.app.ui.channel.ChannelTab
import com.mytube.app.ui.channel.ChannelViewModel
import com.mytube.app.ui.channel.PlaylistsState
import com.mytube.app.ui.channel.PlaylistVideosState
import com.mytube.app.ui.channel.PlaylistVideosViewModel
import com.mytube.app.ui.watch.QueueItem
import com.mytube.app.ui.watch.asQueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The channel page's Playlists tab, and opening one of its lists.
 *
 * The tab is lazy and loads apart from the videos, so the two cannot take each
 * other down; opening a playlist reads its videos from YouTube, so it writes a
 * row before it opens one, exactly as the uploads do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelPlaylistsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun thePlaylistsTabIsLazyAndLoadsOnlyWhenOpened() = runTest(dispatcher) {
        val videos = FakeChannel(playlists = listOf(playlist("PL1", "Retro", 6)))
        val model = ChannelViewModel("chan", videos)
        advanceUntilIdle()

        // Never asked while the videos tab is showing.
        assertEquals(0, videos.playlistsCalls)
        assertIs<PlaylistsState.Idle>(model.playlists.value)

        model.selectTab(ChannelTab.Playlists)
        advanceUntilIdle()

        assertEquals(1, videos.playlistsCalls)
        val ready = assertIs<PlaylistsState.Ready>(model.playlists.value)
        assertEquals(listOf("PL1"), ready.playlists.map { it.id })

        // Switching away and back does not re-ask: the first answer is kept.
        model.selectTab(ChannelTab.Videos)
        model.selectTab(ChannelTab.Playlists)
        advanceUntilIdle()
        assertEquals(1, videos.playlistsCalls)
    }

    @Test
    fun aMorePageOfPlaylistsAppendsAndDeduplicates() = runTest(dispatcher) {
        val videos = FakeChannel(
            playlists = listOf(playlist("PL1", "One", 1)),
            playlistsNext = "p2",
            playlistsPages = mapOf(
                "p2" to (listOf(playlist("PL1", "One", 1), playlist("PL2", "Two", 2)) to ""),
            ),
        )
        val model = ChannelViewModel("chan", videos)
        advanceUntilIdle()
        model.selectTab(ChannelTab.Playlists)
        advanceUntilIdle()

        model.loadMorePlaylists()
        advanceUntilIdle()

        val ready = assertIs<PlaylistsState.Ready>(model.playlists.value)
        // PL1 arrived twice across the two pages; a repeated key is a crash, so
        // it is kept once.
        assertEquals(listOf("PL1", "PL2"), ready.playlists.map { it.id })
        assertTrue(ready.nextPageToken.isEmpty())
    }

    @Test
    fun aPlaylistsFailureLeavesTheVideosAlone() = runTest(dispatcher) {
        val videos = FakeChannel(
            page = listOf(upstream("abc")),
            playlistsError = true,
        )
        val model = ChannelViewModel("chan", videos)
        advanceUntilIdle()
        val videosState = model.state.value

        model.selectTab(ChannelTab.Playlists)
        advanceUntilIdle()

        assertIs<PlaylistsState.Failed>(model.playlists.value)
        // The videos tab is exactly as it was: a playlists fetch that failed did
        // not reach it.
        assertEquals(videosState, model.state.value)
    }

    @Test
    fun openingAPlaylistCarriesItsTitleAndVideos() = runTest(dispatcher) {
        val videos = FakeChannel(
            playlistVideos = PlaylistVideosPage("Retro Tech", listOf(upstream("v1")), ""),
        )
        val model = PlaylistVideosViewModel("chan", "PL1", "from the card", videos)
        advanceUntilIdle()

        val ready = assertIs<PlaylistVideosState.Ready>(model.state.value)
        // The server's title wins over the card's seed once it lands.
        assertEquals("Retro Tech", ready.title)
        assertEquals(listOf("v1"), ready.videos.map { it.id })
    }

    @Test
    fun aPlaylistWithARepeatedVideoKeepsItOnce() = runTest(dispatcher) {
        // A playlist genuinely lists the same video twice, and the offset paging
        // these endpoints do can hand the same row back as well. A `LazyColumn`
        // keyed on the id SIGABRTs on the repeat, so the first page is deduped
        // the same way a later page is.
        val videos = FakeChannel(
            playlistVideos = PlaylistVideosPage(
                "Retro",
                listOf(upstream("v1"), upstream("v2"), upstream("v1")),
                "",
            ),
        )
        val model = PlaylistVideosViewModel("chan", "PL1", "Retro", videos)
        advanceUntilIdle()

        val ready = assertIs<PlaylistVideosState.Ready>(model.state.value)
        assertEquals(listOf("v1", "v2"), ready.videos.map { it.id })
    }

    /** The card's title holds the header until the server sends its own. */
    @Test
    fun openingAPlaylistFallsBackToTheCardTitle() = runTest(dispatcher) {
        val videos = FakeChannel(
            playlistVideos = PlaylistVideosPage("", listOf(upstream("v1")), ""),
        )
        val model = PlaylistVideosViewModel("chan", "PL1", "from the card", videos)
        advanceUntilIdle()

        assertEquals("from the card", assertIs<PlaylistVideosState.Ready>(model.state.value).title)
    }

    @Test
    fun openingAVideoInAPlaylistWritesTheRowFirst() = runTest(dispatcher) {
        val videos = FakeChannel(
            playlistVideos = PlaylistVideosPage("Retro", listOf(upstream("v1")), ""),
        )
        val model = PlaylistVideosViewModel("chan", "PL1", "Retro", videos)
        advanceUntilIdle()

        var opened = ""
        var queue = emptyList<QueueItem>()
        model.openVideo(upstream("v1"), listOf(upstream("v1").asQueueItem())) { id, rest ->
            opened = id
            queue = rest
        }
        advanceUntilIdle()

        assertEquals(1, videos.ensureCalls)
        assertEquals("written-id", opened)
        val row = queue.single()
        assertEquals("written-id", row.id)
        assertTrue(row.inLibrary)
    }

    // --- fakes ---------------------------------------------------------------

    private fun channel() = Channel(id = "chan", name = "Chan", handle = "@chan", avatarPath = "")

    private fun playlist(id: String, title: String, count: Int) =
        ChannelPlaylist(id = id, title = title, thumbnailUrl = "", videoCount = count)

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

    private inner class FakeChannel(
        private val page: List<Video> = emptyList(),
        private val playlists: List<ChannelPlaylist> = emptyList(),
        private val playlistsNext: String = "",
        private val playlistsPages: Map<String, Pair<List<ChannelPlaylist>, String>> = emptyMap(),
        private val playlistsError: Boolean = false,
        private val playlistVideos: PlaylistVideosPage = PlaylistVideosPage("", emptyList(), ""),
    ) : StubVideoRepository() {
        var playlistsCalls = 0
            private set
        var ensureCalls = 0
            private set

        override suspend fun channelPage(channelId: String, sortToken: String, pageToken: String) =
            com.mytube.app.domain.repository.ChannelPage(
                channel = channel(),
                videoCount = page.size,
                videos = page,
                sortOptions = emptyList(),
                nextPageToken = "",
            )

        override suspend fun channelPlaylists(channelId: String, pageToken: String): ChannelPlaylistsPage {
            playlistsCalls++
            if (playlistsError) throw RuntimeException("youtube said no")
            if (pageToken.isNotEmpty()) {
                val (list, next) = playlistsPages[pageToken] ?: (emptyList<ChannelPlaylist>() to "")
                return ChannelPlaylistsPage(list, next)
            }
            return ChannelPlaylistsPage(playlists, playlistsNext)
        }

        override suspend fun playlistVideos(channelId: String, playlistId: String, pageToken: String) =
            playlistVideos

        override suspend fun ensureExternal(sourceUrl: String): String {
            ensureCalls++
            return "written-id"
        }
    }
}
