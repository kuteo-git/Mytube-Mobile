package com.mytube.app.ui.tv

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which rows the television's Home has, decided from what the first load
 * brought back.
 *
 * Two sources decide it — what the server sent and what the phone's chip row
 * would have shown — and getting it wrong draws a row titled "Live" over
 * nothing.
 */
class ShelvesFromTest {

    @Test
    fun `the rows come in the chip row's order, topics last in the server's order`() {
        val shelves = shelvesFrom(
            continueWatching = listOf(video("half")),
            missed = FeedPage(listOf(video("new1")), "m2"),
            live = listOf(video("onAir")),
            recommended = FeedPage(listOf(video("a")), "p2"),
            topics = listOf(Topic("News", 9), Topic("Music", 4)),
        )

        // Compared by kind, not by key: the order is a statement about which
        // shelves, and a key is how the list tells them apart — it changed once
        // already, when topics were namespaced away from the fixed shelves.
        assertEquals(
            listOf(
                ShelfKind.ContinueWatching,
                ShelfKind.Missed,
                ShelfKind.Live,
                ShelfKind.Recommended,
                ShelfKind.OfTopic(Topic("News", 9)),
                ShelfKind.OfTopic(Topic("Music", 4)),
            ),
            shelves.map { it.kind },
        )
    }

    @Test
    fun `each shelf carries its own page token, and lists carry none`() {
        val shelves = shelvesFrom(
            continueWatching = listOf(video("half")),
            missed = FeedPage(listOf(video("new1")), "m2"),
            live = listOf(video("onAir")),
            recommended = FeedPage(listOf(video("a")), "p2"),
            topics = emptyList(),
        ).associateBy { it.kind }

        assertEquals("", shelves.getValue(ShelfKind.ContinueWatching).nextPageToken)
        assertEquals("m2", shelves.getValue(ShelfKind.Missed).nextPageToken)
        assertEquals("", shelves.getValue(ShelfKind.Live).nextPageToken)
        assertEquals("p2", shelves.getValue(ShelfKind.Recommended).nextPageToken)
    }

    @Test
    fun `a topic the server counts no videos for is not a shelf`() {
        val shelves = shelvesFrom(
            continueWatching = emptyList(),
            missed = null,
            live = emptyList(),
            recommended = FeedPage(emptyList(), ""),
            topics = listOf(Topic("Music", 4), Topic("Nothing", 0), Topic("News", 1)),
        )

        assertEquals(
            listOf(ShelfKind.OfTopic(Topic("Music", 4)), ShelfKind.OfTopic(Topic("News", 1))),
            shelves.map { it.kind }.filterIsInstance<ShelfKind.OfTopic>(),
        )
    }

    /** Asked for when the remote reaches them, not with the first load. */
    @Test
    fun `topic shelves arrive waiting, empty and without a token`() {
        val music = shelvesFrom(
            continueWatching = emptyList(),
            missed = null,
            live = emptyList(),
            recommended = FeedPage(emptyList(), ""),
            topics = listOf(Topic("Music", 4)),
        ).last()

        assertEquals(ShelfLoad.Waiting, music.load)
        assertEquals(emptyList(), music.videos)
        assertEquals("", music.nextPageToken)
        assertFalse(music.loadingMore)
        assertFalse(music.canLoadMore)
    }

    /** No answer to Missed is the same row as an empty one: absent. */
    @Test
    fun `missed that could not be asked is an empty, hidden shelf`() {
        val missed = shelvesFrom(
            continueWatching = emptyList(),
            missed = null,
            live = emptyList(),
            recommended = FeedPage(emptyList(), ""),
            topics = emptyList(),
        ).single { it.kind == ShelfKind.Missed }

        assertEquals(ShelfLoad.Ready, missed.load)
        assertEquals(emptyList(), missed.videos)
        assertEquals("", missed.nextPageToken)
        assertFalse(missed.shows)
    }

    // --- Shelf.shows and Shelf.canLoadMore -----------------------------------

    /**
     * Hidden only once it has answered with nothing. Still asking, it says
     * something is coming; failed, it carries the retry.
     */
    @Test
    fun `a shelf is drawn unless it answered with nothing`() {
        assertFalse(shelf(ShelfLoad.Ready, videos = emptyList()).shows)
        assertTrue(shelf(ShelfLoad.Ready, videos = listOf(video("a"))).shows)
        assertTrue(shelf(ShelfLoad.Waiting, videos = emptyList()).shows)
        assertTrue(shelf(ShelfLoad.Loading, videos = emptyList()).shows)
        assertTrue(shelf(ShelfLoad.Failed, videos = emptyList()).shows)
    }

    @Test
    fun `only a ready shelf with a token and nothing in flight can load more`() {
        assertTrue(shelf(ShelfLoad.Ready, token = "p2").canLoadMore)
        assertFalse(shelf(ShelfLoad.Ready, token = "").canLoadMore)
        assertFalse(shelf(ShelfLoad.Ready, token = "p2", loadingMore = true).canLoadMore)
        assertFalse(shelf(ShelfLoad.Loading, token = "p2").canLoadMore)
        assertFalse(shelf(ShelfLoad.Failed, token = "p2").canLoadMore)
        assertFalse(shelf(ShelfLoad.Waiting, token = "p2").canLoadMore)
    }

    private fun shelf(
        load: ShelfLoad,
        videos: List<Video> = listOf(video("a")),
        token: String = "",
        loadingMore: Boolean = false,
    ) = Shelf(ShelfKind.Recommended, videos, token, load, loadingMore)

    private fun video(id: String) = Video(
        id = id,
        title = id,
        channel = Channel(id = "c", name = "c", handle = "@c", avatarPath = ""),
        durationSeconds = 100,
        viewCount = 0,
        publishedAt = "",
        thumbnailPath = "",
    )
}
