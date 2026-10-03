package com.mytube.app.ui

import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.repository.VideoRepository
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
 * The home feed's state holder, with no server anywhere near it.
 *
 * This is what the layering buys. The ViewModel takes `VideoRepository`, an
 * interface `domain` declares, so a few lines of fake stand in for the gateway
 * and every branch — including the ones that only happen when something goes
 * wrong — can be reached in milliseconds.
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
     * The chip pressed last decides the page, whichever answer arrives last.
     *
     * Found by review: `load` kept no job, so a slow answer for a chip somebody
     * had already left landed over the one they moved to.
     */
    @Test
    fun theLastChipPressedWinsOverASlowerEarlierOne() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(
                pages = listOf(page("a", next = "")),
                missedPages = listOf(page("new1", next = "")),
                missedDelays = listOf(0, 1_000, 0),
            ),
        )
        advance()

        model.select(Chip.Missed)
        runCurrent()
        model.select(Chip.All)
        advance()

        assertEquals(Chip.All, assertIs<HomeState.Ready>(model.state.value).selected)
    }

    /**
     * A next page abandoned by a chip switch does not leave the list unable to
     * load more. Cancelling the request skips the line that cleared the flag,
     * and the cached list is a copy of the state that carried it.
     */
    @Test
    fun aSwitchDuringLoadMoreLeavesTheListAbleToLoadMore() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(
                pages = listOf(page("a", "b", next = "p2"), page("c", next = "")),
                feedDelays = listOf(0, 1_000),
                missedPages = listOf(page("new1", next = "m2")),
            ),
        )
        advance()

        // Missed visited once, so going back to it is the cached path.
        model.select(Chip.Missed)
        advance()
        model.select(Chip.All)
        advance()

        model.loadMore()
        runCurrent()
        model.select(Chip.Missed)
        advance()

        assertEquals(false, assertIs<HomeState.Ready>(model.state.value).loadingMore)
    }

    @Test
    fun showsWhatCameBack() = runTest(dispatcher) {
        val model = HomeViewModel(FakeVideos(pages = listOf(page("a", "b", next = ""))))
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
        val model = HomeViewModel(FakeVideos(failWith = ServerNotConfigured()))
        advance()

        assertIs<HomeState.NeedsServer>(model.state.value)
    }

    @Test
    fun aRealFailureSaysSo() = runTest(dispatcher) {
        val model = HomeViewModel(FakeVideos(failWith = IllegalStateException("no route to host")))
        advance()

        assertEquals("no route to host", assertIs<HomeState.Failed>(model.state.value).message)
    }

    @Test
    fun appendsTheNextPage() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(pages = listOf(page("a", next = "t1"), page("b", next = ""))),
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
        val model = HomeViewModel(repository)
        advance()

        repeat(5) { model.loadMore() }
        advance()

        assertEquals(2, repository.calls)
    }

    @Test
    fun aFailedSecondPageLeavesTheFirstOnScreen() = runTest(dispatcher) {
        // Replacing a working list with an error because the *next* page failed
        // takes away something the viewer already had.
        val repository = FakeVideos(
            pages = listOf(page("a", next = "t1")),
            failFromCall = 2,
        )
        val model = HomeViewModel(repository)
        advance()
        model.loadMore()
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("a"), state.videos.map { it.id })
        assertEquals(false, state.loadingMore)
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

    // --- the Missed chip ----------------------------------------------------

    @Test
    fun missedChipIsAbsentWhenNothingWasMissed() = runTest(dispatcher) {
        val model = HomeViewModel(FakeVideos(pages = listOf(page("a", next = ""))))
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(false, state.chips.contains(Chip.Missed))
    }

    /**
     * And it sits straight after All.
     *
     * The position is asserted rather than left to a reader of the code: it is
     * the second thing a thumb reaches and the first after "everything", which
     * is the whole argument for putting a standing question there.
     */
    @Test
    fun missedChipAppearsSecondWhenSomethingWasMissed() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(
                pages = listOf(page("a", next = "")),
                missedPages = listOf(page("new1", "new2", next = "")),
            ),
        )
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf(Chip.All, Chip.Missed), state.chips.take(2))
    }

    /**
     * One request, two jobs.
     *
     * The answer that decided whether to draw the chip is the answer the chip
     * shows. Asking again on selection would be a round trip for a list already
     * in hand, and — worse — a second chance for the two to disagree.
     */
    @Test
    fun selectingMissedShowsWhatTheChipWasBuiltFrom() = runTest(dispatcher) {
        val videos = FakeVideos(
            pages = listOf(page("a", next = "")),
            missedPages = listOf(page("new1", "new2", next = "cursor")),
        )
        val model = HomeViewModel(videos)
        advance()

        model.select(Chip.Missed)
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("new1", "new2"), state.videos.map { it.id })
        assertEquals("cursor", state.nextPageToken)
    }

    /** More of this list comes from this list, not from the feed. */
    @Test
    fun loadingMoreUnderMissedAsksMissed() = runTest(dispatcher) {
        val videos = FakeVideos(
            pages = listOf(page("a", next = "")),
            missedPages = listOf(
                page("new1", next = "cursor"),
                page("new2", next = ""),
            ),
        )
        val model = HomeViewModel(videos)
        advance()
        model.select(Chip.Missed)
        advance()

        val feedCallsBefore = videos.calls
        model.loadMore()
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("new1", "new2"), state.videos.map { it.id })
        assertEquals(feedCallsBefore, videos.calls)
    }

    /**
     * A page that repeats a row must not repeat a key.
     *
     * The missed feed pages by offset, so a video arriving between two requests
     * shifts the window and hands back a row already on screen. `LazyColumn` is
     * keyed on the video id, and a duplicate key is not a cosmetic fault — it
     * throws out of `subcompose` during measure and takes the app with it.
     * Measured on the phone: `EXC_CRASH / SIGABRT`, an unhandled Kotlin
     * exception whose backtrace is
     * `LazyListMeasuredItemProvider.getAndMeasure -> subcompose`.
     */
    @Test
    fun aRepeatedRowIsNotAppendedTwice() = runTest(dispatcher) {
        val videos = FakeVideos(
            pages = listOf(page("a", next = "")),
            missedPages = listOf(
                page("one", "two", next = "cursor"),
                page("two", "three", next = ""),
            ),
        )
        val model = HomeViewModel(videos)
        advance()
        model.select(Chip.Missed)
        advance()
        model.loadMore()
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("one", "two", "three"), state.videos.map { it.id })
    }

    /**
     * A missed list that fails does not take Home down with it.
     *
     * It is asked on every load purely to decide whether one chip exists, and a
     * feed that will not render because of that is a screen lost to a decoration.
     */
    @Test
    fun aFailingMissedCallStillLeavesTheFeed() = runTest(dispatcher) {
        val model = HomeViewModel(
            FakeVideos(pages = listOf(page("a", next = "")), missedFails = true),
        )
        advance()

        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("a"), state.videos.map { it.id })
        assertEquals(false, state.chips.contains(Chip.Missed))
    }

    /**
     * Stepping back to a chip shows what it had and asks nothing.
     *
     * It used to restore the cache and reload underneath, quietly — so a list
     * somebody was already reading changed by itself a moment later. Reported
     * exactly that way. Refreshing is a gesture now, not something that happens
     * to a reader.
     */
    @Test
    fun steppingBackToAChipDoesNotRefetchIt() = runTest(dispatcher) {
        val videos = FakeVideos(pages = listOf(page("a", next = ""), page("b", next = "")))
        val model = HomeViewModel(videos)
        advance()

        model.select(Chip.All)
        advance()
        val callsAfterFirst = videos.calls

        // Away and back. The chip row here holds All and whatever topics came
        // back; Live stands in for "another chip" and asks nothing of the feed.
        model.select(Chip.Live)
        advance()
        model.select(Chip.All)
        advance()

        assertEquals(callsAfterFirst, videos.calls)
        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("a"), state.videos.map { it.id })
    }

    /** A pull is unconditional: it is the gesture that means "ask again". */
    @Test
    fun pullingToRefreshAlwaysAsks() = runTest(dispatcher) {
        val videos = FakeVideos(pages = listOf(page("a", next = ""), page("b", next = "")))
        val model = HomeViewModel(videos)
        advance()
        val before = videos.calls

        model.refresh()
        advance()

        assertEquals(before + 1, videos.calls)
        val state = assertIs<HomeState.Ready>(model.state.value)
        assertEquals(listOf("b"), state.videos.map { it.id })
    }

    private class FakeVideos(
        private val pages: List<FeedPage> = emptyList(),
        private val failWith: Throwable? = null,
        private val failFromCall: Int = Int.MAX_VALUE,
        /** What `/api/feed/missed` answers, page by page. */
        private val missedPages: List<FeedPage> = listOf(FeedPage(emptyList(), "")),
        private val missedFails: Boolean = false,
        /** How long each `feed` call takes, by call number; missing means at once. */
        private val feedDelays: List<Long> = emptyList(),
        /** The same for `missed`. */
        private val missedDelays: List<Long> = emptyList(),
    ) : VideoRepository {
        var calls = 0
            private set
        var missedCalls = 0
            private set

        override suspend fun feed(topic: String, pageToken: String): FeedPage {
            calls++
            feedDelays.getOrNull(calls - 1)?.let { kotlinx.coroutines.delay(it) }
            failWith?.let { throw it }
            if (calls >= failFromCall) throw IllegalStateException("upstream said no")
            return pages.getOrElse(calls - 1) { pages.last() }
        }

        override suspend fun video(id: String) = throw NotImplementedError()
        override suspend fun search(query: String) = throw NotImplementedError()
        override suspend fun discover(query: String, limit: Int) = throw NotImplementedError()
        override suspend fun ensureExternal(sourceUrl: String) = throw NotImplementedError()
        override suspend fun resolveChannel(query: String) = ""

        // The home screen fetches four things at once. A fake that throws for
        // three of them would test error handling rather than the feed, so these
        // answer emptily: no topics, nothing on air, nothing half-watched.
        override suspend fun topics() = emptyList<com.mytube.app.domain.model.Topic>()
        override suspend fun live() = emptyList<Video>()

        /**
         * Keyed on the token, not on a call counter.
         *
         * The counter version was wrong in a way worth recording: every full
         * load asks this — that is what draws the chip — so selecting the chip
         * consumed page two before anybody had scrolled anywhere. A page comes
         * back for the token that asks for it, which is what the server does.
         */
        override suspend fun missed(pageToken: String): FeedPage {
            missedCalls++
            missedDelays.getOrNull(missedCalls - 1)?.let { kotlinx.coroutines.delay(it) }
            if (missedFails) throw IllegalStateException("upstream said no")
            return if (pageToken.isEmpty()) missedPages.first() else missedPages.last()
        }
        override suspend fun history(limit: Int) = emptyList<Video>()
        override suspend fun saved() = emptyList<Video>()
        override suspend fun feedMix() = throw NotImplementedError()
        override suspend fun saveFeedMix(mix: com.mytube.app.domain.repository.FeedMix) = Unit
        override suspend fun subscriptions() = emptyList<com.mytube.app.domain.model.Channel>()
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
        override suspend fun refreshMetadata(videoId: String) = Unit
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
