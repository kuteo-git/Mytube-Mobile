package com.mytube.app.ui

import com.mytube.app.StubVideoRepository
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.search.SearchState
import com.mytube.app.ui.search.SearchViewModel
import com.mytube.app.ui.search.UPSTREAM_PAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
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
 * A search, which asks YouTube and nothing else.
 *
 * Upstream has no cursor so "more" is a larger page, and opening a result is a
 * round trip that has to finish before anything navigates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * A search replaced by the next keystroke is not a failure.
     *
     * Found by review: the request in flight is cancelled when somebody keeps
     * typing, and `runCatching` caught the cancellation like any other error.
     */
    @Test
    fun aSearchCancelledByTypingIsNotAFailure() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = listOf(external("b")), answerAfterMillis = 1_000)
        val model = SearchViewModel(repo)

        model.type("nothing")
        advanceTimeBy(400)
        model.type("nothing phone")
        runCurrent()

        assertIs<SearchState.Idle>(model.state.value)
        assertTrue(model.asking.value)
        advanceUntilIdle()
        assertIs<SearchState.Ready>(model.state.value)
    }

    @Test
    fun asksYouTubeAndNotTheLibrary() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = listOf(external("b")))
        val model = SearchViewModel(repo)

        model.type("nothing phone")
        advanceUntilIdle()

        val ready = assertIs<SearchState.Ready>(model.state.value)
        assertEquals("nothing phone", ready.query)
        assertEquals(listOf("b"), ready.videos.map { it.id })
        assertEquals(1, repo.discoverCalls)
    }

    @Test
    fun oneRequestPerIntent() = runTest(dispatcher) {
        val repo = FakeVideos()
        val model = SearchViewModel(repo)

        // Thirteen keystrokes for one intent. Without the debounce *and* the
        // cancellation this is thirteen yt-dlp runs.
        "nothing phone".forEachIndexed { i, _ -> model.type("nothing phone".take(i + 1)) }
        advanceUntilIdle()

        assertEquals(1, repo.discoverCalls)
    }

    @Test
    fun aSearchYouTubeRefusesSaysSo() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(discoverFails = true))

        model.type("nothing")
        advanceUntilIdle()

        assertIs<SearchState.Failed>(model.state.value)
    }

    @Test
    fun aFullPageMeansThereMayBeMore() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(upstream = List(UPSTREAM_PAGE) { external("v$it") }))

        model.type("nothing")
        advanceUntilIdle()

        assertTrue(assertIs<SearchState.Ready>(model.state.value).hasMore)
    }

    @Test
    fun aShortPageMeansThereIsNoMore() = runTest(dispatcher) {
        // Upstream has no cursor, so this is the only signal there is: an answer
        // shorter than what was asked for is the end of the answers.
        val model = SearchViewModel(FakeVideos(upstream = List(3) { external("v$it") }))

        model.type("nothing")
        advanceUntilIdle()

        assertFalse(assertIs<SearchState.Ready>(model.state.value).hasMore)
    }

    @Test
    fun moreMeansALargerPage() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = List(UPSTREAM_PAGE) { external("v$it") })
        val model = SearchViewModel(repo)

        model.type("nothing")
        advanceUntilIdle()
        model.loadMore()
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
        model.loadMore()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(1, repo.discoverCalls)
    }

    /**
     * A larger page of the last question, landing after a new one was typed,
     * does not replace the new answer: the last question wins.
     */
    @Test
    fun aLargerPageOfTheOldQuestionDoesNotLandOnTheNewOne() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = List(UPSTREAM_PAGE * 2) { external("v$it") }, answerAfterMillis = 1_000)
        val model = SearchViewModel(repo)

        model.type("nothing")
        advanceUntilIdle()
        model.loadMore()
        advanceTimeBy(100)
        model.type("phuong")
        advanceUntilIdle()

        val ready = assertIs<SearchState.Ready>(model.state.value)
        assertEquals("phuong", ready.query)
        assertEquals(UPSTREAM_PAGE, ready.videos.size)
    }

    @Test
    fun aNewQueryStartsAtTheFirstPageAgain() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = List(UPSTREAM_PAGE) { external("v$it") })
        val model = SearchViewModel(repo)

        model.type("nothing")
        advanceUntilIdle()
        model.loadMore()
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

        model.open(external("b"), onOpened = { opened = it })
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

        model.open(external("b").copy(inLibrary = true), onOpened = { opened = it })
        advanceUntilIdle()

        assertEquals("b", opened)
        assertEquals(0, repo.ensureCalls)
    }

    @Test
    fun aSecondTapWhileOpeningIsIgnored() = runTest(dispatcher) {
        val repo = FakeVideos(ensured = "written-id")
        val model = SearchViewModel(repo)

        model.open(external("b"), onOpened = {})
        model.open(external("c"), onOpened = {})
        advanceUntilIdle()

        assertEquals(1, repo.ensureCalls)
    }

    @Test
    fun anOpenThatFailsSaysSoAndNavigatesNowhere() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(ensureFails = true))
        var opened = ""

        model.open(external("b"), onOpened = { opened = it })
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

        model.open(external("b"), onOpened = { opened = it })
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
            FakeVideos(upstream = listOf(external("b")), resolveFails = true),
        )

        model.type("nothing")
        advanceUntilIdle()

        assertEquals("", model.channelPasted.value)
        assertEquals(listOf("b"), assertIs<SearchState.Ready>(model.state.value).videos.map { it.id })
    }

    @Test
    fun clearingGoesBackToIdle() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(upstream = listOf(external("b"))))

        model.type("nothing")
        advanceUntilIdle()
        model.clear()

        assertIs<SearchState.Idle>(model.state.value)
    }

    @Test
    fun suggestionsAreHomesFirstPageAskedOnce() = runTest(dispatcher) {
        val repo = FakeVideos(suggested = listOf(external("s1"), external("s2")))
        val model = SearchViewModel(repo)
        advanceUntilIdle()

        model.type("nothing")
        advanceUntilIdle()
        model.clear()
        advanceUntilIdle()

        assertEquals(listOf("s1", "s2"), model.suggestions.value.map { it.id })
        assertEquals(1, repo.feedCalls)
    }

    @Test
    fun suggestionsThatFailAreNone() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(feedFails = true))
        advanceUntilIdle()

        assertEquals(emptyList(), model.suggestions.value)
    }

    @Test
    fun notInterestedTakesASuggestionOffAndTellsTheFeed() = runTest(dispatcher) {
        val repo = FakeVideos(suggested = listOf(external("s1"), external("s2")))
        val model = SearchViewModel(repo)
        advanceUntilIdle()

        model.notInterested(external("s1"))
        advanceUntilIdle()

        assertEquals(listOf("s2"), model.suggestions.value.map { it.id })
        assertEquals(listOf("s1"), repo.notInterested)
    }

    /**
     * The field spins from the keystroke to the answer — refining a word
     * included, which keeps the last answer on screen as `Ready`.
     */
    @Test
    fun askingCoversTheWaitTheRefineAndALargerPage() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = List(UPSTREAM_PAGE * 2) { external("v$it") }, answerAfterMillis = 1_000)
        val model = SearchViewModel(repo)

        model.type("nothing")
        assertTrue(model.asking.value, "asking from the keystroke")
        advanceUntilIdle()
        assertFalse(model.asking.value, "answered")

        model.type("nothing phone")
        advanceTimeBy(500)
        assertIs<SearchState.Ready>(model.state.value)
        assertTrue(model.asking.value, "refining, with the last answer still up")
        advanceUntilIdle()
        assertFalse(model.asking.value)

        model.loadMore()
        assertTrue(model.asking.value, "a larger page")
        advanceUntilIdle()
        assertFalse(model.asking.value)
    }

    @Test
    fun askingStopsWhenTheFieldIsCleared() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(answerAfterMillis = 1_000))

        model.type("nothing")
        advanceTimeBy(500)
        model.clear()
        assertFalse(model.asking.value)
        model.type("   ")
        assertFalse(model.asking.value, "whitespace is nothing typed")
        assertIs<SearchState.Idle>(model.state.value)
    }

    @Test
    fun aSearchThatFailsStopsAsking() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(discoverFails = true))

        model.type("nothing")
        advanceUntilIdle()

        assertIs<SearchState.Failed>(model.state.value)
        assertFalse(model.asking.value)
    }

    /** A larger page that fails keeps the page already read; the list does not vanish under the reader. */
    @Test
    fun aLargerPageThatFailsKeepsTheList() = runTest(dispatcher) {
        val repo = FakeVideos(upstream = List(UPSTREAM_PAGE) { external("v$it") }, discoverFailsFromCall = 2)
        val model = SearchViewModel(repo)

        model.type("nothing")
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        val ready = assertIs<SearchState.Ready>(model.state.value)
        assertEquals(UPSTREAM_PAGE, ready.videos.size)
        assertFalse(ready.loadingMore)
        assertFalse(model.asking.value)
    }

    @Test
    fun aFailedOpenIsForgottenByTheNextQuestion() = runTest(dispatcher) {
        val model = SearchViewModel(FakeVideos(upstream = listOf(external("b")), ensureFails = true))

        model.type("nothing")
        advanceUntilIdle()
        model.open(external("b"), onOpened = {})
        advanceUntilIdle()
        assertTrue(model.openFailed.value)

        model.type("phuong")
        assertFalse(model.openFailed.value, "typing again")
        model.open(external("b"), onOpened = {})
        advanceUntilIdle()
        model.clear()
        assertFalse(model.openFailed.value, "clearing")
    }

    // --- fixtures -----------------------------------------------------------

    private fun external(id: String) = Video(
        id = id,
        title = id,
        channel = Channel(id = "c", name = "c", handle = "", avatarPath = ""),
        durationSeconds = 1,
        viewCount = 0,
        publishedAt = "",
        thumbnailPath = "https://i.ytimg.com/vi/$id/hq720.jpg",
        sourceUrl = "https://www.youtube.com/watch?v=$id",
        inLibrary = false,
    )

    private class FakeVideos(
        private val upstream: List<Video> = emptyList(),
        private val suggested: List<Video> = emptyList(),
        private val feedFails: Boolean = false,
        private val discoverFails: Boolean = false,
        /** Answers until this call, then refuses: a larger page that fails. */
        private val discoverFailsFromCall: Int = Int.MAX_VALUE,
        private val ensured: String = "written-id",
        private val ensureFails: Boolean = false,
        private val resolved: String = "",
        private val resolveFails: Boolean = false,
        /** How long each search takes to answer, so one can be in flight. */
        private val answerAfterMillis: Long = 0,
    ) : StubVideoRepository() {
        var discoverCalls = 0
            private set
        var feedCalls = 0
            private set
        val notInterested = mutableListOf<String>()
        var ensureCalls = 0
            private set
        var resolveCalls = 0
            private set

        /** Every `limit` this was asked for, in order. Upstream paging is this. */
        val limitsAsked = mutableListOf<Int>()

        override suspend fun discover(query: String, limit: Int): List<Video> {
            discoverCalls++
            limitsAsked += limit
            if (answerAfterMillis > 0) kotlinx.coroutines.delay(answerAfterMillis)
            if (discoverFails || discoverCalls >= discoverFailsFromCall) throw IllegalStateException("could not reach YouTube")
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

        override suspend fun feed(topic: String, pageToken: String): FeedPage {
            feedCalls++
            if (feedFails) throw IllegalStateException("the library said no")
            return FeedPage(suggested, "")
        }

        override suspend fun setNotInterested(videoId: String) {
            notInterested += videoId
        }
        override suspend fun video(id: String) = throw NotImplementedError()
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
