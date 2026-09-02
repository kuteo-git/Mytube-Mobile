package com.mytube.app.ui

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaylistPage
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.playlist.PlaylistState
import com.mytube.app.ui.playlist.PlaylistViewModel
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

/** One collection, and what removing from it means. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun removingTakesTheRowOffThePage() = runTest(dispatcher) {
        val repo = FakePlaylist()
        val model = PlaylistViewModel("pl_music", repo)
        advanceUntilIdle()

        model.remove(video("v1"))
        advanceUntilIdle()

        val ready = assertIs<PlaylistState.Ready>(model.state.value)
        assertEquals(listOf("v2"), ready.videos.map { it.id })
        assertEquals(listOf("pl_music" to "v1"), repo.removed)
    }

    /**
     * A failed removal puts the row back.
     *
     * Unlike the feed's "not interested", which never returns a card: that list
     * is a ranking and the row is gone from the viewer's page either way. This
     * page **is** the playlist, so a video still in it that is not drawn is the
     * screen lying about its own contents.
     */
    @Test
    fun aRefusedRemovalPutsTheRowBack() = runTest(dispatcher) {
        val repo = FakePlaylist(removeFails = true)
        val model = PlaylistViewModel("pl_music", repo)
        advanceUntilIdle()

        model.remove(video("v1"))
        advanceUntilIdle()

        val ready = assertIs<PlaylistState.Ready>(model.state.value)
        assertEquals(listOf("v1", "v2"), ready.videos.map { it.id })
    }

    /** Renaming is drawn first, sent second — and put back if refused. */
    @Test
    fun aRefusedRenameKeepsTheOldName() = runTest(dispatcher) {
        val repo = FakePlaylist(updateFails = true)
        val model = PlaylistViewModel("pl_music", repo)
        advanceUntilIdle()

        model.startRenaming()
        model.nameChanged("Nhạc Việt")
        model.rename()
        advanceUntilIdle()

        val ready = assertIs<PlaylistState.Ready>(model.state.value)
        assertEquals("Nhạc", ready.playlist.title)
    }

    @Test
    fun deletingLeavesThePageWithNothingToDraw() = runTest(dispatcher) {
        val repo = FakePlaylist()
        val model = PlaylistViewModel("pl_music", repo)
        advanceUntilIdle()

        model.askDelete()
        model.delete()
        advanceUntilIdle()

        assertIs<PlaylistState.Deleted>(model.state.value)
        assertEquals(listOf("pl_music"), repo.deleted)
    }

    // A top-level factory rather than a method on the test class: `FakePlaylist`
    // below is a plain nested class, and it needs this too. It was `inner` for
    // one build so it could reach the outer's copy, and the compiler emitted a
    // suspend function that would not verify — `java.lang.VerifyError` on the
    // first call.
    private fun video(id: String) = Video(
        id = id,
        title = id,
        channel = Channel("c", "c", "", ""),
        durationSeconds = 1,
        viewCount = 0,
        publishedAt = "",
        thumbnailPath = "",
    )

    private class FakePlaylist(
        private val removeFails: Boolean = false,
        private val updateFails: Boolean = false,
    ) : VideoRepository {
        val removed = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()

        override suspend fun playlist(playlistId: String, pageToken: String) = PlaylistPage(
            playlist = Playlist(playlistId, "Nhạc", "", 2, false, emptyList()),
            videos = listOf(row("v1"), row("v2")),
            nextPageToken = "",
        )

        private fun row(id: String) = Video(
            id = id,
            title = id,
            channel = Channel("c", "c", "", ""),
            durationSeconds = 1,
            viewCount = 0,
            publishedAt = "",
            thumbnailPath = "",
        )

        override suspend fun removeFromPlaylist(playlistId: String, videoId: String) {
            removed += playlistId to videoId
            if (removeFails) throw IllegalStateException("the library said no")
        }

        override suspend fun updatePlaylist(
            playlistId: String,
            title: String,
            description: String,
        ): Playlist {
            if (updateFails) throw IllegalStateException("the library said no")
            return Playlist(playlistId, title, description, 2, false, emptyList())
        }

        override suspend fun deletePlaylist(playlistId: String) {
            deleted += playlistId
        }

        override suspend fun playlists(videoId: String) = emptyList<Playlist>()
        override suspend fun createPlaylist(title: String) = throw NotImplementedError()
        override suspend fun addToPlaylist(playlistId: String, videoId: String) = Unit

        override suspend fun feed(topic: String, pageToken: String) = throw NotImplementedError()
        override suspend fun video(id: String) = throw NotImplementedError()
        override suspend fun search(query: String) = emptyList<Video>()
        override suspend fun discover(query: String, limit: Int) =
            emptyList<com.mytube.app.domain.model.ExternalVideo>()
        override suspend fun ensureExternal(sourceUrl: String) = ""
        override suspend fun resolveChannel(query: String) = ""
        override suspend fun topics() = emptyList<com.mytube.app.domain.model.Topic>()
        override suspend fun live() = emptyList<Video>()
        override suspend fun missed(pageToken: String) =
            com.mytube.app.domain.repository.FeedPage(emptyList(), "")
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
    }
}
