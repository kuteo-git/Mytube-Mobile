package com.mytube.app

import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.ChannelPage
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.repository.PlaylistPage
import com.mytube.app.domain.repository.VideoRepository

/**
 * A [VideoRepository] that answers every question with nothing.
 *
 * The base for the tests' fakes, each of which overrides only what it is
 * about. Ten files each spelled out all thirty methods, so a new method on the
 * port was ten edits before any test could run, and the one line that mattered
 * in each fake was hidden among thirty that did not.
 *
 * "Nothing" is the honest empty answer where the port has one — no videos, no
 * comments, an empty id. Where it has none (a video, a page, a playlist) the
 * stub refuses, so a test that reaches one it did not set up fails by name
 * rather than passing on a made-up row.
 */
open class StubVideoRepository : VideoRepository {
    override suspend fun feed(topic: String, pageToken: String) = FeedPage(emptyList(), "")
    override suspend fun missed(pageToken: String) = FeedPage(emptyList(), "")
    override suspend fun video(id: String): Video = notStubbed("video")
    override suspend fun search(query: String) = emptyList<Video>()
    override suspend fun discover(query: String, limit: Int) = emptyList<ExternalVideo>()
    override suspend fun ensureExternal(sourceUrl: String) = ""
    override suspend fun resolveChannel(query: String) = ""
    override suspend fun topics() = emptyList<Topic>()
    override suspend fun live() = emptyList<Video>()
    override suspend fun history(limit: Int) = emptyList<Video>()
    override suspend fun saved() = emptyList<Video>()
    override suspend fun subscriptions() = emptyList<Channel>()
    override suspend fun subtitleCues(url: String) = emptyList<SubtitleCue>()
    override suspend fun comments(videoId: String) = emptyList<Comment>()
    override suspend fun importComments(videoId: String) {}
    override suspend fun refreshMetadata(videoId: String) {}
    override suspend fun upNext(videoId: String, channelId: String) = emptyList<Video>()
    override suspend fun recordProgress(videoId: String, positionSeconds: Double, watchedFraction: Double) {}
    override suspend fun setReaction(videoId: String, reaction: Reaction) {}
    override suspend fun setSaved(videoId: String, saved: Boolean) {}
    override suspend fun setSubscribed(channelId: String, subscribed: Boolean) {}
    override suspend fun setNotInterested(videoId: String) {}
    override suspend fun channelPage(channelId: String, sortToken: String, pageToken: String): ChannelPage =
        notStubbed("channelPage")
    override suspend fun playlists(videoId: String) = emptyList<Playlist>()
    override suspend fun playlist(playlistId: String, pageToken: String): PlaylistPage = notStubbed("playlist")
    override suspend fun createPlaylist(title: String): Playlist = notStubbed("createPlaylist")
    override suspend fun updatePlaylist(playlistId: String, title: String, description: String): Playlist =
        notStubbed("updatePlaylist")
    override suspend fun deletePlaylist(playlistId: String) {}
    override suspend fun addToPlaylist(playlistId: String, videoId: String) {}
    override suspend fun removeFromPlaylist(playlistId: String, videoId: String) {}

    private fun notStubbed(what: String): Nothing =
        throw NotImplementedError("$what is not part of this test")
}
