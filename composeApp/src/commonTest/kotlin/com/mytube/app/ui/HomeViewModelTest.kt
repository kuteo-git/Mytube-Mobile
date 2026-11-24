package com.mytube.app.ui

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.repository.ServerNotConfigured
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.ui.home.Chip
import com.mytube.app.ui.home.HomeState
import com.mytube.app.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * One chip's feed, with no server anywhere near it.
 *
 * This is what the layering buys. The ViewModel takes `VideoRepository`, an
 * interface `domain` declares, so a few lines of fake stand in for the gateway
 * and every branch — including the ones that only happen when something goes
 * wrong — can be reached in milliseconds. The chip row is a fixed set now, so
 * nothing here builds it; each instance is one chip, and the kind of chip
 * decides where its page comes from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        // viewModelScope is Dispatchers.Main, which does not exist off-device.
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsWhatCameBack() = runTest(dispatcher) {
        val model = HomeViewModel(FakeVideos(pages = listOf(page("a", "b", next = ""))), Chip.All)
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("a", "b"), state.videos.map { it.id })
        assertEquals("", state.nextPageToken)
    }

    @Test
    fun anUnconfiguredServerIsNotAnError() = runTest(dispatcher) {
        // Nothing has gone wrong when the app has not been set up yet, and the
        // screen's answer is a way to the settings rather than a retry that will
        // fail identically. That is why it is its own state and not a message.
        val model = HomeViewModel(FakeVideos(failWith = ServerNotConfigured()), Chip.All)
        advance()

        assertIs<HomeState.NeedsServer>(model.state.value)
    }

    @Test
    fun aRealFailureSaysSo() = runTest(dispatcher) {
        val model = HomeViewModel(FakeVideos(failWith = IllegalStateException("no route to host")), Chip.All)
        advance()

        assertEquals("no route to host", assertIs<HomeState.Failed>(model.state.value).message)
    }

    @Test
    fun appendsTheNextPage() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(pages = listOf(page("a", next = "t1"), page("b", next = ""))),
            Chip.All,
        )
        advance()
        model.loadMore()
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("a", "b"), state.videos.map { it.id })
    }

    @Test
    fun asksOnceEvenWhenTheListKeepsAsking() = runTest(dispatcher) {
        // A list resting at the bottom of its scroll fires this on every frame.
        // Without the guard a slow answer becomes a dozen identical requests and
        // a dozen copies of the same page.
        val repository = FakeVideos(pages = listOf(page("a", next = "t1"), page("b", next = "")))
        val model = HomeViewModel(repository, Chip.All)
        advance()

        repeat(5) { model.loadMore() }
        advance()

        assertEquals(2, repository.calls)
    }

    @Test
    fun aFailedSecondPageLeavesTheFirstOnScreen() = runTest(dispatcher) {
        // Replacing a working list with an error because the next page failed
        // takes away something the viewer already had.
        val repository = FakeVideos(
            pages = listOf(page("a", next = "t1")),
            failFromCall = 2,
        )
        val model = HomeViewModel(repository, Chip.All)
        advance()
        model.loadMore()
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("a"), state.videos.map { it.id })
        assertEquals(false, state.loadingMore)
    }

    /**
     * A row taken off the page while the next page loads stays off.
     *
     * Found by review: `loadMore` wrote back a copy of the state it read before
     * the request, so "not interested" pressed during the wait came undone the
     * moment the page arrived.
     */
    @Test
    fun aRowRemovedWhileMoreLoadsStaysRemoved() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(
                pages = listOf(page("a", "b", next = "p2"), page("c", next = "")),
                feedDelays = listOf(0, 1_000),
            ),
            Chip.All,
        )
        advance()
        val a = assertIs<HomeState.Ready>(model.state.value).videos.first()

        model.loadMore()
        runCurrent()
        model.notInterested(a)
        advance()

        assertEquals(listOf("b", "c"), assertIs<HomeState.Ready>(model.state.value).videos.map { it.id })
    }

    /**
     * A page that repeats a row must not repeat a key.
     *
     * `LazyColumn` is keyed on the video id, and a duplicate key is not a
     * cosmetic fault — it throws out of `subcompose` during measure and takes
     * the app with it (`EXC_CRASH / SIGABRT`). A feed that pages by offset hands
     * back a row already on screen when something new arrives between requests.
     */
    @Test
    fun aRepeatedRowIsNotAppendedTwice() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(pages = listOf(page("one", "two", next = "cursor"), page("two", "three", next = ""))),
            Chip.All,
        )
        advance()
        model.loadMore()
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("one", "two", "three"), state.videos.map { it.id })
    }

    /** A pull is unconditional: it is the gesture that means "ask again". */
    @Test
    fun pullingToRefreshAlwaysAsks() = runTest(dispatcher) {
        val videos = FakeVideos(pages = listOf(page("a", next = ""), page("b", next = "")))
        val model = HomeViewModel(videos, Chip.All)
        advance()
        val before = videos.calls

        model.refresh()
        advance()

        assertEquals(before + 1, videos.calls)
        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("b"), state.videos.map { it.id })
    }

    /**
     * The answer asked for last wins, whichever arrives last.
     *
     * A slow first load abandoned by a refresh must not land over the refresh's
     * answer. `load` cancels the job in flight before starting another.
     */
    @Test
    fun aRefreshCancelsASlowerEarlierLoad() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(
                pages = listOf(page("stale", next = ""), page("fresh", next = "")),
                feedDelays = listOf(1_000, 0),
            ),
            Chip.All,
        )
        runCurrent()
        model.refresh()
        advance()

        assertEquals(listOf("fresh"), assertIs<HomeState.Ready>(model.state.value).videos.map { it.id })
    }

    // --- the kinds of chip --------------------------------------------------

    /** The Missed chip reads its own endpoint, and pages from it too. */
    @Test
    fun aMissedFeedLoadsFromMissedAndPagesFromIt() = runTest(dispatcher) {
        val videos = FakeVideos(
            // The ranked feed answers too, to prove it is not what is shown.
            pages = listOf(page("ranked", next = "")),
            missedPages = listOf(page("new1", next = "cursor"), page("new2", next = "")),
        )
        val model = HomeViewModel(videos, Chip.Missed)
        advance()

        val feedCallsBefore = videos.calls
        model.loadMore()
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("new1", "new2"), state.videos.map { it.id })
        // Paging came from `missed`, not the ranked feed.
        assertEquals(feedCallsBefore, videos.calls)
    }

    /** Live is a list, not a feed: one answer, and nothing to page. */
    @Test
    fun aLiveFeedShowsWhatIsOnAirAndDoesNotPage() = runTest(dispatcher) {
        val videos = FakeVideos(liveVideos = listOf("stream1", "stream2"))
        val model = HomeViewModel(videos, Chip.Live)
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("stream1", "stream2"), state.videos.map { it.id })
        assertEquals("", state.nextPageToken)

        model.loadMore()
        advance()
        // No page token, so nothing was asked for.
        assertEquals(0, videos.calls)
    }

    /** A category chip filters the ranked feed by its own topic query. */
    @Test
    fun aCategoryFeedAsksItsTopic() = runTest(dispatcher) {
        val videos = FakeVideos(pages = listOf(page("g1", next = "")))
        val model = HomeViewModel(videos, Chip.Category(query = "Gaming", label = "Gaming"))
        advance()

        assertEquals("Gaming", videos.lastTopic)
    }

    private fun kotlinx.coroutines.test.TestScope.advance() {
        testScheduler.advanceUntilIdle()
    }

    private fun page(vararg ids: String, next: String) = FeedPage(
        videos = ids.map { id ->
            Video(
                id = id,
                title = id,
                channel = Channel(id = "c", name = "c", handle = "@c", avatarPath = ""),
                durationSeconds = 1,
                viewCount = 0,
                publishedAt = "",
                thumbnailPath = "",
            )
        },
        nextPageToken = next,
    )

    private class FakeVideos(
        private val pages: List<FeedPage> = emptyList(),
        private val failWith: Throwable? = null,
        private val failFromCall: Int = Int.MAX_VALUE,
        /** What `/api/feed/missed` answers, page by page. */
        private val missedPages: List<FeedPage> = listOf(FeedPage(emptyList(), "")),
        /** What `/api/live` answers, by id. */
        private val liveVideos: List<String> = emptyList(),
        /** How long each `feed` call takes, by call number; missing means at once. */
        private val feedDelays: List<Long> = emptyList(),
    ) : StubVideoRepository() {
        var calls = 0
            private set
        var lastTopic: String? = null
            private set

        override suspend fun feed(topic: String, pageToken: String): FeedPage {
            calls++
            lastTopic = topic
            feedDelays.getOrNull(calls - 1)?.let { kotlinx.coroutines.delay(it) }
            failWith?.let { throw it }
            if (calls >= failFromCall) throw IllegalStateException("upstream said no")
            return pages.getOrElse(calls - 1) { pages.last() }
        }

        override suspend fun missed(pageToken: String): FeedPage =
            if (pageToken.isEmpty()) missedPages.first() else missedPages.last()

        override suspend fun live(): List<Video> =
            liveVideos.map {
                Video(
                    id = it,
                    title = it,
                    channel = Channel(id = "c", name = "c", handle = "@c", avatarPath = ""),
                    durationSeconds = 0,
                    viewCount = 0,
                    publishedAt = "",
                    thumbnailPath = "",
                )
            }

        override suspend fun video(id: String) = throw NotImplementedError()
        override suspend fun discover(query: String, limit: Int) = throw NotImplementedError()
        override suspend fun ensureExternal(sourceUrl: String) = throw NotImplementedError()

        override suspend fun channelPage(
            channelId: String,
            sortToken: String,
            pageToken: String,
        ) = throw NotImplementedError()

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
