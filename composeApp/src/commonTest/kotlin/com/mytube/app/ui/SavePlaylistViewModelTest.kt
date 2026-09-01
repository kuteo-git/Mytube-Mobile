package com.mytube.app.ui

import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaylistPage
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.playlist.SaveSheetState
import com.mytube.app.ui.playlist.SavePlaylistViewModel
import com.mytube.app.ui.playlist.SaveTarget
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
 * The sheet behind the bookmark, and the diff it applies.
 *
 * The diff is the whole thing worth pinning down. Save sends **one request per
 * change** rather than one call carrying the final state, because an endpoint
 * that takes the whole state is one that can empty a playlist when a client is
 * wrong — and because unticking has to really remove, or the tick is a control
 * that lies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavePlaylistViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun tickingOneAddsOne() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = listOf(playlist("pl_music"), playlist("pl_news")))
        val model = SavePlaylistViewModel(SaveTarget("v1"), repo)
        advanceUntilIdle()

        model.toggle("pl_music")
        model.save {}
        advanceUntilIdle()

        assertEquals(listOf("pl_music" to "v1"), repo.added)
        assertTrue(repo.removed.isEmpty())
    }

    /** A tick that does not remove is a control that lies. */
    @Test
    fun untickingOneRemovesIt() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = listOf(playlist("pl_music", contains = true)))
        val model = SavePlaylistViewModel(SaveTarget("v1"), repo)
        advanceUntilIdle()

        model.toggle("pl_music")
        model.save {}
        advanceUntilIdle()

        assertEquals(listOf("pl_music" to "v1"), repo.removed)
        assertTrue(repo.added.isEmpty())
    }

    /**
     * Ticking and unticking again sends nothing, and the button says so.
     *
     * A Save that fires no requests and closes the sheet looks exactly like one
     * that worked — which is how a control that does nothing goes unnoticed.
     */
    @Test
    fun changingYourMindSendsNothing() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = listOf(playlist("pl_music")))
        val model = SavePlaylistViewModel(SaveTarget("v1"), repo)
        advanceUntilIdle()

        model.toggle("pl_music")
        model.toggle("pl_music")
        val ready = assertIs<SaveSheetState.Ready>(model.state.value)
        assertFalse(model.hasChanges(ready))

        model.save {}
        advanceUntilIdle()
        assertTrue(repo.added.isEmpty() && repo.removed.isEmpty())
    }

    /**
     * The pinned row and the playlist rows are independent.
     *
     * The saved shelf is not a playlist — it is the pinned set — so it travels
     * on its own call and must not be confused for one of the rows.
     */
    @Test
    fun theSavedShelfIsItsOwnBit() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = listOf(playlist("pl_music")))
        val model = SavePlaylistViewModel(SaveTarget("v1", saved = false), repo)
        advanceUntilIdle()

        model.toggleSaved()
        model.save {}
        advanceUntilIdle()

        assertEquals(listOf("v1" to true), repo.pinned)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun aPinAlreadySetIsNotSentAgain() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = listOf(playlist("pl_music")))
        val model = SavePlaylistViewModel(SaveTarget("v1", saved = true), repo)
        advanceUntilIdle()

        model.toggle("pl_music")
        model.save {}
        advanceUntilIdle()

        assertTrue(repo.pinned.isEmpty(), "the pinned bit was written for no change")
    }

    /**
     * Naming a list for this video is both acts on one press.
     *
     * The alert's Save creates *and* adds. Leaving the add to a later press of
     * the sheet's own Save would make a named-but-empty playlist the outcome of
     * cancelling — and the sheet is closed while the alert is up, so that press
     * is not even on screen.
     */
    @Test
    fun creatingAlsoAddsTheVideoAndLeavesTheRowTicked() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = emptyList())
        val model = SavePlaylistViewModel(SaveTarget("v1"), repo)
        advanceUntilIdle()

        model.startCreating()
        model.nameChanged("Nhạc")
        model.create()
        advanceUntilIdle()

        val ready = assertIs<SaveSheetState.Ready>(model.state.value)
        assertEquals(listOf("Nhạc"), repo.created)
        assertEquals(listOf("pl_made" to "v1"), repo.added)
        assertTrue(ready.ticked.contains("pl_made"))
        // And it is listed **first**, or the sheet comes back with the row
        // somebody just made below the fold of a capped, scrolling list.
        assertEquals("pl_made", ready.playlists.first().id)
    }

    /** The add already happened, so Save must not send it a second time. */
    @Test
    fun savingAfterCreatingSendsNothingMore() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = emptyList())
        val model = SavePlaylistViewModel(SaveTarget("v1"), repo)
        advanceUntilIdle()

        model.startCreating()
        model.nameChanged("Nhạc")
        model.create()
        advanceUntilIdle()

        model.save {}
        advanceUntilIdle()

        assertEquals(listOf("pl_made" to "v1"), repo.added)
        assertTrue(repo.removed.isEmpty())
    }

    /**
     * An upstream result is written into the catalogue once.
     *
     * Creating a playlist ensures the row, and Save must reuse that id rather
     * than asking the gateway to resolve the same address again.
     */
    @Test
    fun anUpstreamResultIsWrittenOnceAcrossCreateAndSave() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = listOf(playlist("pl_music")), ensured = "v_new")
        val model = SavePlaylistViewModel(
            SaveTarget(videoId = "", sourceUrl = "https://youtu.be/abc"),
            repo,
        )
        advanceUntilIdle()

        model.startCreating()
        model.nameChanged("Nhạc")
        model.create()
        advanceUntilIdle()
        model.toggle("pl_music")
        model.save {}
        advanceUntilIdle()

        assertEquals(1, repo.ensureCalls.size)
        assertEquals(listOf("pl_made" to "v_new", "pl_music" to "v_new"), repo.added)
    }

    /**
     * An upstream result has no catalogue row, so one is written first — and
     * the **returned** id is what the adds use.
     */
    @Test
    fun anUpstreamResultIsEnsuredBeforeItIsAdded() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = listOf(playlist("pl_music")), ensured = "v_new")
        val model = SavePlaylistViewModel(
            SaveTarget(videoId = "", sourceUrl = "https://youtu.be/abc"),
            repo,
        )
        advanceUntilIdle()

        model.toggle("pl_music")
        model.save {}
        advanceUntilIdle()

        assertEquals(listOf("https://youtu.be/abc"), repo.ensureCalls)
        assertEquals(listOf("pl_music" to "v_new"), repo.added)
    }

    /**
     * A refusal wearing a success's clothes.
     *
     * The gateway answers 200 with an empty id when it could not resolve the
     * address. Adding with it would write playlist rows naming no video.
     */
    @Test
    fun anEnsureThatResolvedNothingAddsNothing() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = listOf(playlist("pl_music")), ensured = "")
        val model = SavePlaylistViewModel(
            SaveTarget(videoId = "", sourceUrl = "https://youtu.be/abc"),
            repo,
        )
        advanceUntilIdle()

        model.toggle("pl_music")
        model.save {}
        advanceUntilIdle()

        assertTrue(repo.added.isEmpty())
    }

    /** An upstream result is in no list yet, so it asks about none. */
    @Test
    fun anUpstreamSheetDoesNotAskAboutMembership() = runTest(dispatcher) {
        val repo = FakePlaylists(lists = emptyList())
        SavePlaylistViewModel(SaveTarget(videoId = "", sourceUrl = "https://youtu.be/abc"), repo)
        advanceUntilIdle()

        assertEquals(listOf(""), repo.askedAbout)
    }

    private fun playlist(id: String, contains: Boolean = false) = Playlist(
        id = id,
        title = id,
        description = "",
        itemCount = 0,
        containsVideo = contains,
        thumbnailPaths = emptyList(),
    )

    private class FakePlaylists(
        private val lists: List<Playlist>,
        private val ensured: String = "",
    ) : VideoRepository {
        val askedAbout = mutableListOf<String>()
        val added = mutableListOf<Pair<String, String>>()
        val removed = mutableListOf<Pair<String, String>>()
        val pinned = mutableListOf<Pair<String, Boolean>>()
        val created = mutableListOf<String>()
        val ensureCalls = mutableListOf<String>()

        override suspend fun playlists(videoId: String): List<Playlist> {
            askedAbout += videoId
            return lists
        }

        override suspend fun addToPlaylist(playlistId: String, videoId: String) {
            added += playlistId to videoId
        }

        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) {
            removed += playlistId to videoId
        }

        override suspend fun setSaved(videoId: String, saved: Boolean) {
            pinned += videoId to saved
        }

        override suspend fun createPlaylist(title: String): Playlist {
            created += title
            return Playlist("pl_made", title, "", 0, containsVideo = false, thumbnailPaths = emptyList())
        }

        override suspend fun ensureExternal(sourceUrl: String): String {
            ensureCalls += sourceUrl
            return ensured
        }

        override suspend fun playlist(playlistId: String, pageToken: String): PlaylistPage =
            throw NotImplementedError()
        override suspend fun updatePlaylist(
            playlistId: String,
            title: String,
            description: String,
        ) = throw NotImplementedError()
        override suspend fun deletePlaylist(playlistId: String) = Unit

        override suspend fun feed(topic: String, pageToken: String) = throw NotImplementedError()
        override suspend fun video(id: String) = throw NotImplementedError()
        override suspend fun search(query: String) = emptyList<Video>()
        override suspend fun discover(query: String, limit: Int) =
            emptyList<com.mytube.app.domain.model.ExternalVideo>()
        override suspend fun resolveChannel(query: String) = ""
        override suspend fun topics() = emptyList<com.mytube.app.domain.model.Topic>()
        override suspend fun live() = emptyList<Video>()
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
    }
}
