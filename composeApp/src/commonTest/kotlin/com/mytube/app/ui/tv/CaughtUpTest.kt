package com.mytube.app.ui.tv

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which shelves Home holds after catching up, coming back from the player.
 *
 * Two answers meet here — what is on screen and what the server just said —
 * and each kind of shelf takes a different one: the fixed shelves are what
 * watching changed, so they are taken fresh; a topic shelf was asked for by
 * the remote and maybe paged since, so it keeps itself. Taken the wrong way
 * round, either Continue watching misses what was just watched or a topic row
 * drops back to its skeleton under the remote.
 */
class CaughtUpTest {

    private val music = ShelfKind.OfTopic(Topic("Music", 3))
    private val news = ShelfKind.OfTopic(Topic("News", 2))

    @Test
    fun `the fixed shelves are taken from the fresh answer, appended pages and all`() {
        val now = listOf(shelf(ShelfKind.Recommended, "a", "b", "c", token = "p3"))
        val fresh = listOf(shelf(ShelfKind.Recommended, "x", token = "q2"))

        val result = caughtUp(now, fresh).single()

        assertEquals(listOf("x"), result.videos.map { it.id })
        assertEquals("q2", result.nextPageToken)
    }

    @Test
    fun `a topic shelf already on screen keeps its cards and its place in the pages`() {
        val now = listOf(shelf(music, "m1", "m2", token = "mp3"))
        val fresh = listOf(waiting(music))

        val result = caughtUp(now, fresh).single()

        assertEquals(ShelfLoad.Ready, result.load)
        assertEquals(listOf("m1", "m2"), result.videos.map { it.id })
        assertEquals("mp3", result.nextPageToken)
    }

    @Test
    fun `the order is the fresh answer's`() {
        val now = listOf(shelf(ShelfKind.Recommended, "a"), shelf(music, "m1"), shelf(news, "n1"))
        val fresh = listOf(shelf(ShelfKind.Recommended, "a"), waiting(news), waiting(music))

        assertEquals(listOf("__all", "topic:News", "topic:Music"), caughtUp(now, fresh).map { it.kind.key })
    }

    @Test
    fun `a topic missing from the fresh answer goes`() {
        val now = listOf(shelf(music, "m1"), shelf(news, "n1"))
        val fresh = listOf(waiting(news))

        assertEquals(listOf("topic:News"), caughtUp(now, fresh).map { it.kind.key })
    }

    @Test
    fun `a topic new in the fresh answer arrives waiting`() {
        val now = listOf(shelf(music, "m1"))
        val fresh = listOf(waiting(music), waiting(news))

        val added = caughtUp(now, fresh).single { it.kind.key == "topic:News" }

        assertEquals(ShelfLoad.Waiting, added.load)
        assertEquals(emptyList(), added.videos)
    }

    /**
     * Keys are namespaced so a topic called `__all` is not the recommended
     * shelf. Kept by a bare name, the topic's cards would have replaced the
     * fresh ranked feed.
     */
    @Test
    fun `a topic named like a fixed shelf does not stand in for it`() {
        val clash = ShelfKind.OfTopic(Topic("__all", 3))
        val now = listOf(shelf(clash, "t1"), shelf(ShelfKind.Recommended, "old"))
        val fresh = listOf(shelf(ShelfKind.Recommended, "new"), waiting(clash))

        val result = caughtUp(now, fresh)

        assertEquals(listOf("new"), result.single { it.kind == ShelfKind.Recommended }.videos.map { it.id })
        assertEquals(listOf("t1"), result.single { it.kind == clash }.videos.map { it.id })
    }

    @Test
    fun `nothing on screen takes the fresh answer whole`() {
        val fresh = listOf(shelf(ShelfKind.Recommended, "a"), waiting(music))

        assertEquals(fresh, caughtUp(emptyList(), fresh))
    }

    private fun shelf(kind: ShelfKind, vararg ids: String, token: String = "") =
        Shelf(kind, ids.map(::video), token, ShelfLoad.Ready, loadingMore = false)

    private fun waiting(kind: ShelfKind) = Shelf(kind, emptyList(), "", ShelfLoad.Waiting, loadingMore = false)

    private fun video(id: String) = Video(
        id = id,
        title = id,
        channel = Channel(id = "c", name = "c", handle = "@c", avatarPath = ""),
        durationSeconds = 100,
        viewCount = 0,
        publishedAt = "",
        thumbnailPath = "",
        watchedFraction = 0.0,
    )
}
