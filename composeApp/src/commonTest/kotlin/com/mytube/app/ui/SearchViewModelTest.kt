package com.mytube.app.ui

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.search.SearchState
import com.mytube.app.ui.search.SearchViewModel
import com.mytube.app.ui.search.UPSTREAM_PAGE
import com.mytube.app.ui.search.UpstreamState
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The two halves of a search, and the ways they come apart.
 *
 * The library answers off a local index; YouTube is a yt-dlp run over the
 * internet. Everything below is about that difference: either half can fail
 * without taking the other with it, upstream has no cursor so "more" is a larger
 * page, and opening an upstream result is a round trip that has to finish before
 * anything navigates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun asksBothHalvesForEveryQuery() = runTest(dispatcher) {
        val repo = FakeVideos(local = listOf(video("a")), upstream = listOf(external("b")))
        val model = SearchViewModel(repo)

        model.type("nothing phone")
        advanceUntilIdle()

        assertEquals(listOf("a"), assertIs<SearchState.Ready>(model.state.value).videos.map { it.id })
        assertEquals(listOf("b"), assertIs<UpstreamState.Ready>(model.upstream.value).videos.map { it.id })
        // Upstream runs on every search rather than only when the library comes
        // up empty. The gateway's own note says why: topics decide what the feed
        // offers, and searching is how somebody looks past that.
        assertEquals(1, repo.discoverCalls)
    }

    @Test
    fun oneRequestPerIntent() = runTest(dispatcher) {
        val repo = FakeVideos()
        val model = SearchViewModel(repo)

        // Thirteen keystrokes for one intent. Without the debounce *and* the
        // cancellation this is thirteen searches, thirteen behaviour signals,
        // and thirteen yt-dlp runs.
        "nothing phone".forEachIndexed { i, _ -> model.type("nothing phone".take(i + 1)) }
        advanceUntilIdle()

        assertEquals(1, repo.searchCalls)
        assertEquals(1, repo.discoverCalls)
    }

    @Test
    fun theLibraryStillAnswersWhenYouTubeWillNot() = runTest(dispatcher) {
        val model = SearchViewModel(
            FakeVideos(local = listOf(video("a")), discoverFails = true),
        )

        model.type("nothing")
        advanceUntilIdle()

        // The half that worked is still on screen. One state for both halves
        // would have thrown this away with the half that did not.
        assertEquals(listOf("a"), assertIs<SearchState.Ready>(model.state.value).videos.map { it.id })
        assertIs<UpstreamState.Failed>(model.upstream.value)
    }

    @Test
    fun youTubeStillAnswersWhenTheLibraryWillNot() = runTest(dispatcher) {
        val model = SearchViewModel(
            FakeVideos(upstream = listOf(external("b")), searchFails = true),
        )

        model.type("nothing")
        advanceUntilIdle()

        assertIs<SearchState.Failed>(model.state.value)
        assertEquals(listOf("b"), assertIs<UpstreamState.Ready>(model.upstream.value).videos.map { it.id })
    }

    @Test
    fun aFullPageMeansThereMayBeMore() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(upstream = List(UPSTREAM_PAGE) { external("v$it") }))

        model.type("nothing")
        advanceUntilIdle()

        assertTrue(assertIs<UpstreamState.Ready>(model.upstream.value).hasMore)
    }

    @Test
    fun aShortPageMeansThereIsNoMore() = runTest(dispatcher) {
        // Upstream has no cursor, so this is the only signal there is: an answer
        // shorter than what was asked for is the end of the answers.
        val model = SearchViewModel(FakeVideos(upstream = List(3) { external("v$it") }))

        model.type("nothing")
        advanceUntilIdle()

        assertFalse(assertIs<UpstreamState.Ready>(model.upstream.value).hasMore)
    }

    @Test
    fun moreMeansALargerPage() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = List(UPSTREAM_PAGE) { external("v$it") })
        val model = SearchViewModel(repo)

        model.type("nothing")
        advanceUntilIdle()
        model.loadMoreUpstream()
        advanceUntilIdle()

        assertEquals(listOf(UPSTREAM_PAGE, UPSTREAM_PAGE * 2), repo.limitsAsked)
    }

    @Test
    fun aFinishedListIsNotAskedAgain() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = List(3) { external("v$it") })
        val model = SearchViewModel(repo)

        model.type("nothing")
        advanceUntilIdle()
        // Reaching the bottom of a list that has run out happens on every scroll
        // and must cost nothing.
        model.loadMoreUpstream()
        model.loadMoreUpstream()
        advanceUntilIdle()

        assertEquals(1, repo.discoverCalls)
    }

    @Test
    fun aNewQueryStartsAtTheFirstPageAgain() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = List(UPSTREAM_PAGE) { external("v$it") })
        val model = SearchViewModel(repo)

        model.type("nothing")
        advanceUntilIdle()
        model.loadMoreUpstream()
        advanceUntilIdle()
        model.type("phuong")
        advanceUntilIdle()

        // Without the reset, refining a word inherits however large the previous
        // search had grown — a hundred results for a word just typed.
        assertEquals(listOf(UPSTREAM_PAGE, UPSTREAM_PAGE * 2, UPSTREAM_PAGE), repo.limitsAsked)
    }

    @Test
    fun openingWritesTheRowFirstAndThenNavigates() = runTest(dispatcher) {
        val repo = FakeVideos(ensured = "written-id")
        val model = SearchViewModel(repo)
        var opened = ""

        model.openExternal(external("b"), onOpened = { opened = it })
        // While the gateway is asking YouTube, the card says so and nothing has
        // navigated: the watch screen would have nothing to load.
        assertEquals("https://www.youtube.com/watch?v=b", model.opening.value)
        assertEquals("", opened)

        advanceUntilIdle()
        assertEquals("written-id", opened)
        assertEquals("", model.opening.value)
    }

    @Test
    fun aVideoAlreadyInTheLibraryIsOpenedWithoutARoundTrip() = runTest(dispatcher) {
        val repo = FakeVideos()
        val model = SearchViewModel(repo)
        var opened = ""

        model.openExternal(external("b").copy(inLibrary = true), onOpened = { opened = it })
        advanceUntilIdle()

        assertEquals("b", opened)
        assertEquals(0, repo.ensureCalls)
    }

    @Test
    fun aSecondTapWhileOpeningIsIgnored() = runTest(dispatcher) {
        val repo = FakeVideos(ensured = "written-id")
        val model = SearchViewModel(repo)

        model.openExternal(external("b"), onOpened = {})
        model.openExternal(external("c"), onOpened = {})
        advanceUntilIdle()

        assertEquals(1, repo.ensureCalls)
    }

    @Test
    fun anOpenThatFailsSaysSoAndNavigatesNowhere() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(ensureFails = true))
        var opened = ""

        model.openExternal(external("b"), onOpened = { opened = it })
        advanceUntilIdle()

        assertEquals("", opened)
        assertEquals("", model.opening.value)
        assertTrue(model.openFailed.value)
    }

    /**
     * An empty id is a refusal wearing a success's clothes.
     *
     * `ensureExternal` answers 200 with `{"videoId":""}` if the gateway could
     * not resolve the address, and navigating to an empty id would open the
     * watch screen on nothing.
     */
    @Test
    fun anEmptyIdIsNotAnOpening() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(ensured = ""))
        var opened = "untouched"

        model.openExternal(external("b"), onOpened = { opened = it })
        advanceUntilIdle()

        assertEquals("untouched", opened)
        assertTrue(model.openFailed.value)
    }

    /**
     * A pasted channel address leaves this screen.
     *
     * It is not a question and there is nothing on this page that answers it:
     * `/api/discover` reads no video out of a channel URL and returns nothing,
     * so without this a pasted channel link is a blank page.
     */
    @Test
    fun aPastedChannelAddressNamesAChannel() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(resolved = "UCBJycsmduvYEL83R_U4JriQ"))

        model.type("https://www.youtube.com/@mkbhd")
        advanceUntilIdle()

        assertEquals("UCBJycsmduvYEL83R_U4JriQ", model.channelPasted.value)
    }

    @Test
    fun anOrdinarySearchNamesNoChannel() = runTest(dispatcher) {
        // The gateway is asked about every query and answers `{"channel":null}`
        // for most of them, which is not an error and must not read as one.
        val repo = FakeVideos(resolved = "")
        val model = SearchViewModel(repo)

        model.type("nothing phone")
        advanceUntilIdle()

        assertEquals("", model.channelPasted.value)
        assertEquals(1, repo.resolveCalls)
    }

    @Test
    fun theScreenSaysWhenItHasGoneThere() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(resolved = "UC123"))

        model.type("https://www.youtube.com/@mkbhd")
        advanceUntilIdle()
        model.channelOpened()

        // Cleared, or coming back to this screen would send the viewer straight
        // out of it again.
        assertEquals("", model.channelPasted.value)
    }

    @Test
    fun aResolveThatFailsIsSilent() = runTest(dispatcher) {
        // Nothing is wrong with a search that could not be checked for being an
        // address, and the two halves that matter have already answered.
        val model = SearchViewModel(
            FakeVideos(local = listOf(video("a")), resolveFails = true),
        )

        model.type("nothing")
        advanceUntilIdle()

        assertEquals("", model.channelPasted.value)
        assertEquals(listOf("a"), assertIs<SearchState.Ready>(model.state.value).videos.map { it.id })
    }

    @Test
    fun clearingPutsBothHalvesBackToIdle() = runTest(dispatcher) {
        val model = SearchViewModel(
            FakeVideos(local = listOf(video("a")), upstream = listOf(external("b"))),
        )

        model.type("nothing")
        advanceUntilIdle()
        model.clear()

        assertIs<SearchState.Idle>(model.state.value)
        assertIs<UpstreamState.Idle>(model.upstream.value)
    }

    // --- fixtures -----------------------------------------------------------

    private fun video(id: String) = Video(
        id = id,
        title = id,
        channel = Channel(id = "c", name = "c", handle = "", avatarPath = ""),
        durationSeconds = 1,
        viewCount = 0,
        publishedAt = "",
        thumbnailPath = "",
    )

    private fun external(id: String) = ExternalVideo(
        id = id,
        title = id,
        channelName = "c",
        durationSeconds = 1,
        viewCount = 0,
        thumbnailUrl = "https://i.ytimg.com/vi/$id/hq720.jpg",
        sourceUrl = "https://www.youtube.com/watch?v=$id",
        inLibrary = false,
    )

    private class FakeVideos(
        private val local: List<Video> = emptyList(),
        private val upstream: List<ExternalVideo> = emptyList(),
        private val searchFails: Boolean = false,
        private val discoverFails: Boolean = false,
        private val ensured: String = "written-id",
        private val ensureFails: Boolean = false,
        private val resolved: String = "",
        private val resolveFails: Boolean = false,
    ) : VideoRepository {
        var searchCalls = 0
            private set
        var discoverCalls = 0
            private set
        var ensureCalls = 0
            private set
        var resolveCalls = 0
            private set

        /** Every `limit` this was asked for, in order. Upstream paging is this. */
        val limitsAsked = mutableListOf<Int>()

        override suspend fun search(query: String): List<Video> {
            searchCalls++
            if (searchFails) throw IllegalStateException("the library said no")
            return local
        }

        override suspend fun discover(query: String, limit: Int): List<ExternalVideo> {
            discoverCalls++
            limitsAsked += limit
            if (discoverFails) throw IllegalStateException("could not reach YouTube")
            return upstream.take(limit)
        }

        override suspend fun ensureExternal(sourceUrl: String): String {
            ensureCalls++
            if (ensureFails) throw IllegalStateException("could not reach YouTube")
            return ensured
        }

        override suspend fun resolveChannel(query: String): String {
            resolveCalls++
            if (resolveFails) throw IllegalStateException("could not reach the library")
            return resolved
        }

        override suspend fun feed(topic: String, pageToken: String) = throw NotImplementedError()
        override suspend fun video(id: String) = throw NotImplementedError()
        override suspend fun topics() = emptyList<com.mytube.app.domain.model.Topic>()
        override suspend fun live() = emptyList<Video>()
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
        override suspend fun channelPage(
            channelId: String,
            sortToken: String,
            pageToken: String,
        ) = throw NotImplementedError()

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
