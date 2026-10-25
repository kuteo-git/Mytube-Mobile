package com.mytube.app.ui.tv

import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * The television's Home: many shelves, each with its own page and its own request.
 *
 * The phone's Home holds one list; this holds one per chip, and every way the
 * phone's tests found for one list to go wrong — a slow answer landing late, a
 * page repeating a row, a decoration taking the screen down — now has a shelf's
 * worth of places to happen in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TvHomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        // viewModelScope is Dispatchers.Main, which does not exist off-device.
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // --- the first load ------------------------------------------------------

    @Test
    fun `the first load lays the shelves out in the chip row's order`() = runTest(dispatcher) {
        val model = TvHomeViewModel(
            FakeVideos(
                history = listOf(video("half", watched = 0.5)),
                missed = mapOf("" to page("new1", next = "")),
                live = listOf(video("onAir")),
                feed = mapOf(("" to "") to page("a", next = "")),
                topics = listOf(Topic("Music", 4), Topic("News", 2)),
            ),
        )
        advance()

        assertEquals(
            listOf(
                ShelfKind.ContinueWatching,
                ShelfKind.Missed,
                ShelfKind.Live,
                ShelfKind.Recommended,
                ShelfKind.OfTopic(Topic("Music", 4)),
                ShelfKind.OfTopic(Topic("News", 2)),
            ),
            ready(model).shelves.map { it.kind },
        )
    }

    /**
     * Only what somebody is in the middle of, and no more than a row's worth.
     *
     * History is everything watched — finished, barely started and half-way —
     * and offering to "continue" a video that was watched to the end is
     * offering to resume something nobody is in the middle of.
     */
    @Test
    fun `continue watching keeps only videos in progress, at most twelve`() = runTest(dispatcher) {
        val history = buildList {
            add(video("finished", watched = 1.0))
            add(video("untouched", watched = 0.0))
            repeat(15) { add(video("p$it", watched = 0.5)) }
        }
        val model = TvHomeViewModel(FakeVideos(history = history))
        advance()

        val shelf = shelf(model, ShelfKind.ContinueWatching)
        assertEquals((0 until 12).map { "p$it" }, shelf.videos.map { it.id })
    }

    /**
     * A titled row with nothing under it is the empty grid a chip with no
     * videos would have led to. The rows still exist in the list — `shows` is
     * what the screen asks — so the order of everything after them is stable.
     */
    @Test
    fun `empty missed, live and continue watching shelves are not shown`() = runTest(dispatcher) {
        val model = TvHomeViewModel(
            FakeVideos(feed = mapOf(("" to "") to page("a", next = ""))),
        )
        advance()

        assertFalse(shelf(model, ShelfKind.ContinueWatching).shows)
        assertFalse(shelf(model, ShelfKind.Missed).shows)
        assertFalse(shelf(model, ShelfKind.Live).shows)
        assertTrue(shelf(model, ShelfKind.Recommended).shows)
    }

    @Test
    fun `a topic with no videos is not a shelf`() = runTest(dispatcher) {
        val model = TvHomeViewModel(
            FakeVideos(topics = listOf(Topic("Music", 3), Topic("Empty", 0))),
        )
        advance()

        val topics = ready(model).shelves.mapNotNull { (it.kind as? ShelfKind.OfTopic)?.topic?.name }
        assertEquals(listOf("Music"), topics)
    }

    @Test
    fun `an unconfigured server asks for one rather than failing`() = runTest(dispatcher) {
        val model = TvHomeViewModel(FakeVideos(failWith = ServerNotConfigured()))
        advance()

        assertIs<TvHomeState.NeedsServer>(model.state.value)
    }

    @Test
    fun `any other failure says what went wrong`() = runTest(dispatcher) {
        val model = TvHomeViewModel(FakeVideos(failWith = IllegalStateException("no route to host")))
        advance()

        assertEquals("no route to host", assertIs<TvHomeState.Failed>(model.state.value).message)
    }

    /**
     * Missed is asked on every load only to decide whether its row exists. A
     * Home that will not render because of that is a screen lost to a
     * decoration — the phone's rule, and the same test.
     */
    @Test
    fun `a failing missed call does not fail Home`() = runTest(dispatcher) {
        val model = TvHomeViewModel(
            FakeVideos(
                missedFails = true,
                feed = mapOf(("" to "") to page("a", next = "")),
            ),
        )
        advance()

        val missed = shelf(model, ShelfKind.Missed)
        assertEquals(emptyList(), missed.videos)
        assertFalse(missed.shows)
        assertEquals(listOf("a"), shelf(model, ShelfKind.Recommended).videos.map { it.id })
    }

    @Test
    fun `refresh after a failure asks again`() = runTest(dispatcher) {
        val videos = FakeVideos(failWith = IllegalStateException("down"))
        val model = TvHomeViewModel(videos)
        advance()
        assertIs<TvHomeState.Failed>(model.state.value)

        videos.failWith = null
        model.refresh()
        advance()

        assertIs<TvHomeState.Ready>(model.state.value)
    }

    // --- topic shelves, asked for when the remote reaches them ---------------

    /**
     * Waiting, not Loading, and nothing asked: a dozen topics cost a dozen
     * requests only for somebody who scrolls through all of them.
     */
    @Test
    fun `topic shelves wait until they are reached`() = runTest(dispatcher) {
        val videos = FakeVideos(topics = listOf(Topic("Music", 3)))
        val model = TvHomeViewModel(videos)
        advance()

        val music = shelf(model, ShelfKind.OfTopic(Topic("Music", 3)))
        assertEquals(ShelfLoad.Waiting, music.load)
        assertEquals(emptyList(), music.videos)
        assertTrue(music.shows, "a shelf still to be asked for is drawn, with skeletons")
        assertEquals(emptyList(), videos.feedCalls.filter { it.first == "Music" })
    }

    @Test
    fun `reaching a topic shelf fetches its first page`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val model = TvHomeViewModel(
            FakeVideos(
                topics = listOf(music.topic),
                feed = mapOf(("Music" to "") to page("m1", "m2", next = "mp2")),
            ),
        )
        advance()

        model.ensureLoaded(music)
        // Before the request has had a chance to run: the shelf says it is
        // asking the moment it is reached, not once the answer is on its way.
        assertEquals(ShelfLoad.Loading, shelf(model, music).load)
        advance()

        val shelf = shelf(model, music)
        assertEquals(ShelfLoad.Ready, shelf.load)
        assertEquals(listOf("m1", "m2"), shelf.videos.map { it.id })
        assertEquals("mp2", shelf.nextPageToken)
    }

    /**
     * A topic is the library's own label, so nothing stops one being called
     * what a fixed shelf's key happens to be. When a topic's key was its bare
     * name, `__all` was the recommended shelf's key as well: reaching the topic
     * found the recommended shelf first and fetched nothing, and the shelf list
     * carried one lazy key twice — which is a crash, not a repetition.
     */
    @Test
    fun `a topic named like a fixed shelf is still its own shelf`() = runTest(dispatcher) {
        val clash = ShelfKind.OfTopic(Topic("__all", 3))
        val model = TvHomeViewModel(
            FakeVideos(
                topics = listOf(clash.topic),
                feed = mapOf(
                    ("" to "") to page("r1", next = ""),
                    ("__all" to "") to page("t1", next = ""),
                ),
            ),
        )
        advance()

        model.ensureLoaded(clash)
        advance()

        val shelves = ready(model).shelves
        assertEquals(shelves.size, shelves.map { it.kind.key }.toSet().size, "every shelf key is unique")
        assertEquals(listOf("r1"), shelves.single { it.kind == ShelfKind.Recommended }.videos.map { it.id })
        val topic = shelves.single { it.kind == clash }
        assertEquals(ShelfLoad.Ready, topic.load)
        assertEquals(listOf("t1"), topic.videos.map { it.id })
    }

    /**
     * The row calls this as it is composed, and a row is composed again on
     * every scroll past it. Without the guard each pass would be a request and
     * a fresh first page dropped over whatever had been appended.
     */
    @Test
    fun `a topic shelf is fetched once however often it is reached`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val videos = FakeVideos(
            topics = listOf(music.topic),
            feed = mapOf(("Music" to "") to page("m1", next = "")),
        )
        val model = TvHomeViewModel(videos)
        advance()

        // Twice while the first is in flight, and again once it has landed.
        model.ensureLoaded(music)
        model.ensureLoaded(music)
        advance()
        model.ensureLoaded(music)
        advance()

        assertEquals(listOf("Music" to ""), videos.feedCalls.filter { it.first == "Music" })
    }

    @Test
    fun `ensureLoaded on a shelf that came with the first load asks nothing`() = runTest(dispatcher) {
        val videos = FakeVideos(feed = mapOf(("" to "") to page("a", next = "p2")))
        val model = TvHomeViewModel(videos)
        advance()
        val before = videos.feedCalls.size

        model.ensureLoaded(ShelfKind.Recommended)
        model.ensureLoaded(ShelfKind.Missed)
        advance()

        assertEquals(before, videos.feedCalls.size)
    }

    @Test
    fun `a topic shelf that fails says so, and retry asks again`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val videos = FakeVideos(
            topics = listOf(music.topic),
            feed = mapOf(("Music" to "") to page("m1", next = "")),
            feedFails = mutableSetOf("Music" to ""),
        )
        val model = TvHomeViewModel(videos)
        advance()

        model.ensureLoaded(music)
        advance()
        assertEquals(ShelfLoad.Failed, shelf(model, music).load)
        assertTrue(shelf(model, music).shows, "a failed shelf is drawn: it has the retry")

        videos.feedFails.clear()
        model.retry(music)
        advance()

        val shelf = shelf(model, music)
        assertEquals(ShelfLoad.Ready, shelf.load)
        assertEquals(listOf("m1"), shelf.videos.map { it.id })
        assertEquals(2, videos.feedCalls.count { it == ("Music" to "") })
    }

    /** Retry is for a shelf that failed; on one that worked it would throw away appended pages. */
    @Test
    fun `retry on a shelf that did not fail asks nothing`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val videos = FakeVideos(
            topics = listOf(music.topic),
            feed = mapOf(("Music" to "") to page("m1", next = "")),
        )
        val model = TvHomeViewModel(videos)
        advance()
        model.ensureLoaded(music)
        advance()

        model.retry(music)
        model.retry(ShelfKind.Recommended)
        advance()

        assertEquals(1, videos.feedCalls.count { it.first == "Music" })
        assertEquals(1, videos.feedCalls.count { it == ("" to "") })
    }

    // --- the next page of a shelf --------------------------------------------

    /**
     * Merged, never concatenated: a video ranked onto two pages is one repeated
     * lazy key, and a repeated key is a SIGABRT rather than a repetition.
     */
    @Test
    fun `more of the recommended shelf comes from the feed, without repeating a row`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(
                ("" to "") to page("a", "b", next = "p2"),
                ("" to "p2") to page("b", "c", next = "p3"),
            ),
        )
        val model = TvHomeViewModel(videos)
        advance()

        model.loadMore(ShelfKind.Recommended)
        advance()

        val shelf = shelf(model, ShelfKind.Recommended)
        assertEquals(listOf("a", "b", "c"), shelf.videos.map { it.id })
        assertEquals("p3", shelf.nextPageToken)
        assertFalse(shelf.loadingMore)
        assertEquals("" to "p2", videos.feedCalls.last())
    }

    /** More of this list comes from this list: the feed is a different question. */
    @Test
    fun `more of the missed shelf comes from missed, not from the feed`() = runTest(dispatcher) {
        val videos = FakeVideos(
            missed = mapOf(
                "" to page("new1", next = "cursor"),
                "cursor" to page("new2", next = ""),
            ),
        )
        val model = TvHomeViewModel(videos)
        advance()
        val feedBefore = videos.feedCalls.toList()

        model.loadMore(ShelfKind.Missed)
        advance()

        val shelf = shelf(model, ShelfKind.Missed)
        assertEquals(listOf("new1", "new2"), shelf.videos.map { it.id })
        assertEquals("", shelf.nextPageToken)
        assertEquals(listOf("", "cursor"), videos.missedCalls)
        assertEquals(feedBefore, videos.feedCalls)
    }

    @Test
    fun `more of a topic shelf comes from that topic's feed`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val videos = FakeVideos(
            topics = listOf(music.topic),
            feed = mapOf(
                ("Music" to "") to page("m1", next = "mp2"),
                ("Music" to "mp2") to page("m2", next = ""),
            ),
        )
        val model = TvHomeViewModel(videos)
        advance()
        model.ensureLoaded(music)
        advance()

        model.loadMore(music)
        advance()

        assertEquals(listOf("m1", "m2"), shelf(model, music).videos.map { it.id })
        assertEquals("Music" to "mp2", videos.feedCalls.last())
    }

    /** Lists, not feeds: everything they have arrived in the first answer. */
    @Test
    fun `live and continue watching never ask for more`() = runTest(dispatcher) {
        val videos = FakeVideos(
            history = listOf(video("half", watched = 0.5)),
            live = listOf(video("onAir")),
        )
        val model = TvHomeViewModel(videos)
        advance()
        val feedBefore = videos.feedCalls.size
        val missedBefore = videos.missedCalls.size

        model.loadMore(ShelfKind.Live)
        model.loadMore(ShelfKind.ContinueWatching)
        runCurrent()

        assertFalse(shelf(model, ShelfKind.Live).loadingMore)
        assertFalse(shelf(model, ShelfKind.ContinueWatching).loadingMore)
        advance()
        assertEquals(feedBefore, videos.feedCalls.size)
        assertEquals(missedBefore, videos.missedCalls.size)
    }

    @Test
    fun `a shelf with no next page asks for none`() = runTest(dispatcher) {
        val videos = FakeVideos(feed = mapOf(("" to "") to page("a", next = "")))
        val model = TvHomeViewModel(videos)
        advance()

        model.loadMore(ShelfKind.Recommended)
        advance()

        assertEquals(listOf("" to ""), videos.feedCalls)
    }

    /**
     * Focus resting on the last card fires this on every move. Without the
     * guard a slow answer becomes a run of identical requests.
     */
    @Test
    fun `asking for more while a page is on its way asks once`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(
                ("" to "") to page("a", next = "p2"),
                ("" to "p2") to page("b", next = "p3"),
            ),
            feedDelays = mapOf(("" to "p2") to 1_000L),
        )
        val model = TvHomeViewModel(videos)
        advance()

        model.loadMore(ShelfKind.Recommended)
        runCurrent()
        assertTrue(shelf(model, ShelfKind.Recommended).loadingMore)
        repeat(4) { model.loadMore(ShelfKind.Recommended) }
        advance()

        assertEquals(1, videos.feedCalls.count { it == ("" to "p2") })
        assertEquals(listOf("a", "b"), shelf(model, ShelfKind.Recommended).videos.map { it.id })
    }

    /** Taking away what is on screen because the *next* page did not arrive is the phone feed's rule too. */
    @Test
    fun `a next page that fails leaves the shelf as it was`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(("" to "") to page("a", "b", next = "p2")),
            feedFails = mutableSetOf("" to "p2"),
        )
        val model = TvHomeViewModel(videos)
        advance()
        val before = shelf(model, ShelfKind.Recommended)

        model.loadMore(ShelfKind.Recommended)
        advance()

        val after = shelf(model, ShelfKind.Recommended)
        assertEquals(before, after)
        assertEquals(ShelfLoad.Ready, after.load)
        assertFalse(after.loadingMore)
        assertTrue(after.canLoadMore, "the same page can be asked for again")
    }

    /**
     * Two shelves answering at once both land.
     *
     * Each answer is merged into the state as it is *now*. Written back from a
     * copy read before the request, the second answer would erase the first.
     */
    @Test
    fun `two shelves fetching at once both keep their answers`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val news = ShelfKind.OfTopic(Topic("News", 3))
        val model = TvHomeViewModel(
            FakeVideos(
                topics = listOf(music.topic, news.topic),
                feed = mapOf(
                    ("Music" to "") to page("m1", next = ""),
                    ("News" to "") to page("n1", next = ""),
                ),
                feedDelays = mapOf(("Music" to "") to 500L, ("News" to "") to 100L),
            ),
        )
        advance()

        model.ensureLoaded(music)
        model.ensureLoaded(news)
        advance()

        assertEquals(listOf("m1"), shelf(model, music).videos.map { it.id })
        assertEquals(listOf("n1"), shelf(model, news).videos.map { it.id })
    }

    // --- the last question wins ----------------------------------------------

    /**
     * A shelf's page that was asked for before a refresh never lands after it.
     *
     * It would otherwise be appended to the *new* first page — rows from a
     * ranking the server has since replaced, under a token it no longer means.
     */
    @Test
    fun `a page in flight when Home refreshes never lands in the new state`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(
                ("" to "") to page("a", next = "p2"),
                ("" to "p2") to page("stale", next = "p3"),
            ),
            feedDelays = mapOf(("" to "p2") to 1_000L),
        )
        val model = TvHomeViewModel(videos)
        advance()

        model.loadMore(ShelfKind.Recommended)
        runCurrent()
        videos.feed[("" to "")] = page("fresh", next = "q2")
        model.refresh()
        advance()

        val shelf = shelf(model, ShelfKind.Recommended)
        assertEquals(listOf("fresh"), shelf.videos.map { it.id })
        assertEquals("q2", shelf.nextPageToken)
        assertFalse(shelf.loadingMore)
    }

    @Test
    fun `a topic's first page in flight when Home refreshes never lands in the new state`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val videos = FakeVideos(
            topics = listOf(music.topic),
            feed = mapOf(("Music" to "") to page("m1", next = "")),
            feedDelays = mapOf(("Music" to "") to 1_000L),
        )
        val model = TvHomeViewModel(videos)
        advance()

        model.ensureLoaded(music)
        runCurrent()
        model.refresh()
        advance()

        // The new Home's shelf is waiting to be reached again, not holding the
        // answer to a question the old Home asked.
        val shelf = shelf(model, music)
        assertEquals(ShelfLoad.Waiting, shelf.load)
        assertEquals(emptyList(), shelf.videos)
    }

    // --- coming back to Home --------------------------------------------------

    /**
     * Reported from the sofa: a video watched from Home, put away, and Continue
     * watching still did not have it. Home stays composed under the player, so
     * nothing asked again; and asking again through `refresh` would put the
     * skeleton up and take the shelves — and the card focus was on — away.
     */
    @Test
    fun `catching up brings the shelves up to date without a loading screen`() = runTest(dispatcher) {
        val videos = FakeVideos(
            missed = mapOf("" to page("new1", "new2", next = "")),
            feed = mapOf(("" to "") to page("a", next = "")),
        )
        val model = TvHomeViewModel(videos)
        advance()

        videos.history = listOf(video("new1", watched = 0.4))
        videos.missed = mapOf("" to page("new2", next = ""))
        model.catchUp()
        runCurrent()
        assertIs<TvHomeState.Ready>(model.state.value)
        advance()

        assertEquals(listOf("new1"), shelf(model, ShelfKind.ContinueWatching).videos.map { it.id })
        assertEquals(listOf("new2"), shelf(model, ShelfKind.Missed).videos.map { it.id })
    }

    /**
     * A topic shelf somebody has already scrolled through keeps its cards: it
     * arriving `Waiting` again would draw its skeleton under the remote, and its
     * second page would be gone with the row focus was in.
     */
    @Test
    fun `catching up keeps a topic shelf that has already loaded`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val videos = FakeVideos(
            feed = mapOf(("Music" to "") to page("m1", "m2", next = "")),
            topics = listOf(Topic("Music", 3)),
        )
        val model = TvHomeViewModel(videos)
        advance()
        model.ensureLoaded(music)
        advance()

        model.catchUp()
        advance()

        val shelf = shelf(model, music)
        assertEquals(ShelfLoad.Ready, shelf.load)
        assertEquals(listOf("m1", "m2"), shelf.videos.map { it.id })
    }

    @Test
    fun `catching up that fails leaves the shelves as they were`() = runTest(dispatcher) {
        val videos = FakeVideos(feed = mapOf(("" to "") to page("a", next = "")))
        val model = TvHomeViewModel(videos)
        advance()

        videos.failWith = IllegalStateException("gateway went away")
        model.catchUp()
        advance()

        assertEquals(listOf("a"), shelf(model, ShelfKind.Recommended).videos.map { it.id })
    }

    /**
     * Nothing on screen to keep, so nothing to be quiet about: a Home that
     * failed, or never had a server, is asked again from the start — the
     * loading screen included, since what it covers is an error, not shelves.
     */
    @Test
    fun `catching up from a failed Home goes through a full load`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(("" to "") to page("a", next = "")),
            failWith = IllegalStateException("down"),
        )
        val model = TvHomeViewModel(videos)
        advance()
        assertIs<TvHomeState.Failed>(model.state.value)

        videos.failWith = null
        model.catchUp()
        assertIs<TvHomeState.Loading>(model.state.value)
        advance()

        assertEquals(listOf("a"), shelf(model, ShelfKind.Recommended).videos.map { it.id })
    }

    @Test
    fun `catching up from a Home that needs a server goes through a full load`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(("" to "") to page("a", next = "")),
            failWith = ServerNotConfigured(),
        )
        val model = TvHomeViewModel(videos)
        advance()
        assertIs<TvHomeState.NeedsServer>(model.state.value)

        videos.failWith = null
        model.catchUp()
        advance()

        assertEquals(listOf("a"), shelf(model, ShelfKind.Recommended).videos.map { it.id })
    }

    /**
     * A fixed shelf's next page belongs to the answer being replaced. Landing
     * after the catch-up it would be appended to the *fresh* first page, under
     * a token the server no longer means.
     */
    @Test
    fun `a next page in flight when Home catches up never lands on the fresh first page`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(
                ("" to "") to page("a", next = "p2"),
                ("" to "p2") to page("stale", next = "p3"),
            ),
            feedDelays = mapOf(("" to "p2") to 1_000L),
        )
        val model = TvHomeViewModel(videos)
        advance()

        model.loadMore(ShelfKind.Recommended)
        runCurrent()
        assertTrue(shelf(model, ShelfKind.Recommended).loadingMore)
        videos.feed[("" to "")] = page("fresh", next = "q2")
        model.catchUp()
        advance()

        val shelf = shelf(model, ShelfKind.Recommended)
        assertEquals(listOf("fresh"), shelf.videos.map { it.id })
        assertEquals("q2", shelf.nextPageToken)
        assertFalse(shelf.loadingMore)
        assertTrue(shelf.canLoadMore, "the fresh first page can be followed by its own next page")
    }

    /**
     * The catch-up cancels the fixed shelves' next pages before it knows it
     * will have anything to put in their place. When it then fails, the shelf
     * it leaves behind must still be able to ask for its next page — otherwise
     * `loadingMore` stays up with nothing in flight, and the row never grows
     * again until Home is restarted.
     */
    @Test
    fun `a catch-up that fails while a next page is in flight leaves the shelf able to ask again`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(
                ("" to "") to page("a", next = "p2"),
                ("" to "p2") to page("b", next = "p3"),
            ),
            feedDelays = mapOf(("" to "p2") to 1_000L),
        )
        val model = TvHomeViewModel(videos)
        advance()

        model.loadMore(ShelfKind.Recommended)
        runCurrent()
        videos.failWith = IllegalStateException("gateway went away")
        model.catchUp()
        advance()

        val shelf = shelf(model, ShelfKind.Recommended)
        assertFalse(shelf.loadingMore, "nothing is in flight any more")
        assertTrue(shelf.canLoadMore)
    }

    /**
     * Shelves stay on screen while Home catches up, so the remote can reach a
     * row's end in the meantime — and that next page is asked with the old
     * answer's token. Landing after the catch-up, it would put the old
     * ranking's second page after the new first one.
     */
    @Test
    fun `a next page asked for while Home is catching up does not land on the fresh first page`() = runTest(dispatcher) {
        val videos = FakeVideos(
            feed = mapOf(
                ("" to "") to page("a", next = "p2"),
                ("" to "p2") to page("stale", next = "p3"),
            ),
            feedDelays = mapOf(("" to "") to 1_000L, ("" to "p2") to 2_000L),
        )
        val model = TvHomeViewModel(videos)
        advance()

        videos.feed[("" to "")] = page("fresh", next = "q2")
        model.catchUp()
        runCurrent()
        model.loadMore(ShelfKind.Recommended)
        advance()

        val shelf = shelf(model, ShelfKind.Recommended)
        assertEquals(listOf("fresh"), shelf.videos.map { it.id })
        assertEquals("q2", shelf.nextPageToken)
    }

    @Test
    fun `a topic the server no longer has goes when Home catches up`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val videos = FakeVideos(
            topics = listOf(music.topic, Topic("News", 2)),
            feed = mapOf(("Music" to "") to page("m1", next = "")),
        )
        val model = TvHomeViewModel(videos)
        advance()
        model.ensureLoaded(music)
        advance()

        videos.topics = listOf(Topic("News", 2))
        model.catchUp()
        advance()

        val keys = ready(model).shelves.map { it.kind.key }
        assertEquals(listOf("__continue", "__missed", "__live", "__all", "topic:News"), keys)
    }

    @Test
    fun `a topic new since the first load arrives waiting when Home catches up`() = runTest(dispatcher) {
        val videos = FakeVideos(topics = listOf(Topic("Music", 3)))
        val model = TvHomeViewModel(videos)
        advance()

        videos.topics = listOf(Topic("Music", 3), Topic("Cooking", 5))
        model.catchUp()
        advance()

        val cooking = shelf(model, ShelfKind.OfTopic(Topic("Cooking", 5)))
        assertEquals(ShelfLoad.Waiting, cooking.load)
        assertEquals(emptyList(), cooking.videos)
        assertEquals(emptyList(), videos.feedCalls.filter { it.first == "Cooking" })
    }

    /**
     * A topic's first page is that shelf's own question, not one the catch-up
     * replaces: still on its way when the catch-up lands, it lands after it.
     */
    @Test
    fun `a topic shelf still loading when Home catches up lands afterwards`() = runTest(dispatcher) {
        val music = ShelfKind.OfTopic(Topic("Music", 3))
        val videos = FakeVideos(
            topics = listOf(music.topic),
            feed = mapOf(("Music" to "") to page("m1", "m2", next = "mp2")),
            feedDelays = mapOf(("Music" to "") to 1_000L),
        )
        val model = TvHomeViewModel(videos)
        advance()

        model.ensureLoaded(music)
        runCurrent()
        model.catchUp()
        runCurrent()
        assertEquals(ShelfLoad.Loading, shelf(model, music).load, "the catch-up has landed; the topic has not")
        advance()

        val shelf = shelf(model, music)
        assertEquals(ShelfLoad.Ready, shelf.load)
        assertEquals(listOf("m1", "m2"), shelf.videos.map { it.id })
        assertEquals("mp2", shelf.nextPageToken)
        assertEquals(1, videos.feedCalls.count { it == ("Music" to "") })
    }

    // --- helpers -------------------------------------------------------------

    private fun TestScope.advance() = testScheduler.advanceUntilIdle()

    private fun ready(model: TvHomeViewModel) = assertIs<TvHomeState.Ready>(model.state.value)

    private fun shelf(model: TvHomeViewModel, kind: ShelfKind): Shelf =
        ready(model).shelves.single { it.kind.key == kind.key }

    private fun video(id: String, watched: Double = 0.0) = Video(
        id = id,
        title = id,
        channel = Channel(id = "c", name = "c", handle = "@c", avatarPath = ""),
        durationSeconds = 100,
        viewCount = 0,
        publishedAt = "",
        thumbnailPath = "",
        watchedFraction = watched,
    )

    private fun page(vararg ids: String, next: String) = FeedPage(ids.map { video(it) }, next)

    /**
     * The gateway, answering by what it is asked rather than by call count.
     *
     * Keyed on (topic, pageToken) for the feed and on the token for missed —
     * which is what the server does, and what keeps a test that asks in a
     * different order from consuming a page meant for another question. Every
     * call is recorded, so a test can say what was *not* asked.
     */
    private class FakeVideos(
        var history: List<Video> = emptyList(),
        var missed: Map<String, FeedPage> = mapOf("" to FeedPage(emptyList(), "")),
        val missedFails: Boolean = false,
        val live: List<Video> = emptyList(),
        feed: Map<Pair<String, String>, FeedPage> = emptyMap(),
        val feedFails: MutableSet<Pair<String, String>> = mutableSetOf(),
        val feedDelays: Map<Pair<String, String>, Long> = emptyMap(),
        var topics: List<Topic> = emptyList(),
        /** Thrown by every first-load call, as an unreachable server would. */
        var failWith: Throwable? = null,
    ) : VideoRepository {
        val feed = feed.toMutableMap()
        val feedCalls = mutableListOf<Pair<String, String>>()
        val missedCalls = mutableListOf<String>()

        override suspend fun feed(topic: String, pageToken: String): FeedPage {
            val key = topic to pageToken
            feedCalls += key
            feedDelays[key]?.let { delay(it) }
            failWith?.let { throw it }
            if (key in feedFails) throw IllegalStateException("upstream said no")
            return feed[key] ?: FeedPage(emptyList(), "")
        }

        override suspend fun missed(pageToken: String): FeedPage {
            missedCalls += pageToken
            failWith?.let { throw it }
            if (missedFails) throw IllegalStateException("upstream said no")
            return missed[pageToken] ?: FeedPage(emptyList(), "")
        }

        override suspend fun history(limit: Int): List<Video> {
            failWith?.let { throw it }
            return history
        }

        override suspend fun live(): List<Video> {
            failWith?.let { throw it }
            return live
        }

        override suspend fun topics(): List<Topic> {
            failWith?.let { throw it }
            return topics
        }

        override suspend fun video(id: String) = throw NotImplementedError()
        override suspend fun search(query: String) = throw NotImplementedError()
        override suspend fun discover(query: String, limit: Int) = throw NotImplementedError()
        override suspend fun ensureExternal(sourceUrl: String) = throw NotImplementedError()
        override suspend fun resolveChannel(query: String) = ""
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
