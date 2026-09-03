package com.mytube.app.data.repository

import com.mytube.app.data.remote.GatewayDataSource
import com.mytube.app.data.remote.dto.toDomain
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.parseWebVtt
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.model.Narration
import com.mytube.app.data.remote.dto.FeedMixDto
import com.mytube.app.domain.repository.ChannelPage
import com.mytube.app.domain.repository.FeedMix
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.ServerRepository
import com.mytube.app.domain.repository.PlaylistPage
import com.mytube.app.domain.repository.SortOption
import com.mytube.app.domain.repository.VideoRepository

/**
 * Videos, fetched from the gateway and handed over as domain types.
 *
 * ## Why it depends on ServerRepository rather than on a base URL
 *
 * The address can change while the app runs, and the profile changes when
 * somebody switches who they are — which, per the server charter, must clear
 * everything cached under the previous person. Reading both per call means there
 * is no stale copy to invalidate, and the cost is two key reads against a
 * request that is about to cross the network anyway.
 *
 * ## What is deliberately absent
 *
 * No caching. It belongs above this — a screen knows when it is asking again for
 * the same page and a repository does not — and the server charter has a
 * cautionary tale about the alternative: the web app's hover prefetch wrote its
 * answer under the player's own cache key, so hovering a card and then opening
 * it meant the real request was never issued at all.
 */
class NarrationRepositoryImpl(
    private val gateway: GatewayDataSource,
    private val server: ServerRepository,
) : NarrationRepository {

    override suspend fun start(videoId: String, fromSeconds: Double) =
        gateway.startNarration(requireBase(), server.profileId(), videoId, fromSeconds)

    override suspend fun state(videoId: String): Narration {
        val base = requireBase()
        return gateway.narration(base, server.profileId(), videoId).toDomain(base)
    }

    private suspend fun requireBase(): String =
        server.baseUrl().ifBlank { throw ServerNotConfigured() }
}

class VideoRepositoryImpl(
    private val gateway: GatewayDataSource,
    private val server: ServerRepository,
) : VideoRepository {

    override suspend fun feed(topic: String, pageToken: String): FeedPage {
        val dto = gateway.feed(requireBaseUrl(), server.profileId(), topic, pageToken)
        return FeedPage(
            videos = dto.videos.map { it.toDomain() },
            // The wire's null and the wire's blank string mean the same thing —
            // no more pages — and the domain has one way of saying it.
            nextPageToken = dto.nextPageToken.orEmpty().trim(),
        )
    }

    override suspend fun missed(pageToken: String): FeedPage {
        val dto = gateway.missed(requireBaseUrl(), server.profileId(), pageToken)
        return FeedPage(
            videos = dto.videos.map { it.toDomain() },
            nextPageToken = dto.nextPageToken.orEmpty().trim(),
        )
    }

    override suspend fun video(id: String): Video =
        gateway.video(requireBaseUrl(), server.profileId(), id).toDomain()

    override suspend fun search(query: String): List<Video> =
        gateway.search(requireBaseUrl(), server.profileId(), query).map { it.toDomain() }

    override suspend fun discover(query: String, limit: Int): List<ExternalVideo> =
        gateway.discover(requireBaseUrl(), server.profileId(), query, limit)
            .videos.map { it.toDomain() }

    override suspend fun ensureExternal(sourceUrl: String): String =
        gateway.ensureExternal(requireBaseUrl(), server.profileId(), sourceUrl)

    override suspend fun resolveChannel(query: String): String =
        gateway.resolveChannel(requireBaseUrl(), server.profileId(), query)

    override suspend fun topics(): List<Topic> =
        gateway.topics(requireBaseUrl(), server.profileId()).topics.map { it.toDomain() }

    override suspend fun live(): List<Video> =
        gateway.live(requireBaseUrl(), server.profileId()).videos.map { it.toDomain() }

    /**
     * A page of history is enough for the rail.
     *
     * The rail shows at most a dozen and history comes back newest first, so a
     * deeper read would be pages fetched to be thrown away. 24 is one page and
     * leaves room for the finished videos that get filtered out of it.
     */
    override suspend fun history(limit: Int): List<Video> =
        gateway.history(requireBaseUrl(), server.profileId(), limit = limit)
            .videos.map { it.toDomain() }

    override suspend fun subscriptions(): List<Channel> =
        gateway.subscriptions(requireBaseUrl(), server.profileId())
            .channels.map { it.toDomain() }

    override suspend fun saved(): List<Video> =
        gateway.saved(requireBaseUrl(), server.profileId()).videos.map { it.toDomain() }

    override suspend fun feedMix(): FeedMix {
        val dto = gateway.feedMix(requireBaseUrl(), server.profileId())
        return FeedMix(
            subscribedPercent = dto.subscribedPercent,
            affinityPercent = dto.affinityPercent,
            discoveryPercent = dto.discoveryPercent,
            fixedPercent = dto.fixedShares.continueWatching +
                dto.fixedShares.rewatch + dto.fixedShares.freshSubscribed,
        )
    }

    override suspend fun saveFeedMix(mix: FeedMix) = gateway.saveFeedMix(
        requireBaseUrl(),
        server.profileId(),
        FeedMixDto(
            subscribedPercent = mix.subscribedPercent,
            affinityPercent = mix.affinityPercent,
            discoveryPercent = mix.discoveryPercent,
        ),
    )

    override suspend fun subtitleCues(url: String): List<SubtitleCue> =
        parseWebVtt(gateway.subtitleFile(url))

    override suspend fun comments(videoId: String): List<Comment> =
        gateway.comments(requireBaseUrl(), server.profileId(), videoId)
            .comments.map { it.toDomain() }

    override suspend fun importComments(videoId: String) =
        gateway.importComments(requireBaseUrl(), server.profileId(), videoId)

    override suspend fun upNext(videoId: String, channelId: String): List<Video> =
        gateway.upNext(requireBaseUrl(), server.profileId(), videoId, channelId)
            .videos.map { it.toDomain() }

    override suspend fun recordProgress(
        videoId: String,
        positionSeconds: Double,
        watchedFraction: Double,
    ) = gateway.recordProgress(
        requireBaseUrl(), server.profileId(), videoId, positionSeconds, watchedFraction,
    )

    override suspend fun setReaction(videoId: String, reaction: Reaction) =
        gateway.setReaction(
            requireBaseUrl(),
            server.profileId(),
            videoId,
            // The server's own three words. Mapped here rather than by naming
            // the enum constants after the wire, so a server rename touches this
            // line and nothing else.
            when (reaction) {
                Reaction.Like -> "LIKE"
                Reaction.Dislike -> "DISLIKE"
                Reaction.None -> "NONE"
            },
        )

    override suspend fun setSaved(videoId: String, saved: Boolean) =
        gateway.setSaved(requireBaseUrl(), server.profileId(), videoId, saved)

    override suspend fun setNotInterested(videoId: String) =
        gateway.setNotInterested(requireBaseUrl(), server.profileId(), videoId)

    override suspend fun setSubscribed(channelId: String, subscribed: Boolean) =
        gateway.setSubscribed(requireBaseUrl(), server.profileId(), channelId, subscribed)

    override suspend fun channelPage(
        channelId: String,
        sortToken: String,
        pageToken: String,
    ): ChannelPage {
        val base = requireBaseUrl()
        val user = server.profileId()
        // In sequence, not in parallel: the videos cannot be mapped without the
        // channel, since the listing carries none of its own.
        val detail = gateway.channel(base, user, channelId)
        val page = gateway.channelVideos(base, user, channelId, channelToken(sortToken, pageToken))
        val channel = detail.channel.toDomain()
        return ChannelPage(
            channel = channel,
            videoCount = detail.videoCount,
            videos = page.videos.map { it.toDomain(channel) },
            sortOptions = page.sortOptions.map { SortOption(it.label, it.token) },
            nextPageToken = page.nextPageToken,
        )
    }

    override suspend fun playlists(videoId: String): List<Playlist> =
        gateway.playlists(requireBaseUrl(), server.profileId(), videoId)
            .playlists.map { it.toDomain() }

    override suspend fun playlist(playlistId: String, pageToken: String): PlaylistPage {
        val page = gateway.playlist(requireBaseUrl(), server.profileId(), playlistId, pageToken)
        return PlaylistPage(
            playlist = page.playlist.toDomain(),
            videos = page.videos.map { it.toDomain() },
            nextPageToken = page.nextPageToken,
        )
    }

    override suspend fun createPlaylist(title: String): Playlist =
        gateway.createPlaylist(requireBaseUrl(), server.profileId(), title).toDomain()

    override suspend fun updatePlaylist(
        playlistId: String,
        title: String,
        description: String,
    ): Playlist =
        gateway.updatePlaylist(requireBaseUrl(), server.profileId(), playlistId, title, description)
            .toDomain()

    override suspend fun deletePlaylist(playlistId: String) =
        gateway.deletePlaylist(requireBaseUrl(), server.profileId(), playlistId)

    override suspend fun addToPlaylist(playlistId: String, videoId: String) =
        gateway.addPlaylistItem(requireBaseUrl(), server.profileId(), playlistId, videoId)

    override suspend fun removeFromPlaylist(playlistId: String, videoId: String) =
        gateway.removePlaylistItem(requireBaseUrl(), server.profileId(), playlistId, videoId)

    /**
     * Refusing early, with a distinct type.
     *
     * Before anybody has set an address there is nothing to request, and the
     * honest answer is not a network failure — the network is fine. A screen
     * that catches this can send somebody to the settings; one that sees a
     * connection error will tell them to check their wifi.
     */
    private suspend fun requireBaseUrl(): String =
        server.baseUrl().ifBlank { throw ServerNotConfigured() }
}

class ServerNotConfigured : Exception("no server address has been set")

/**
 * The one token a channel listing is asked for, from the two the caller has.
 *
 * The caller genuinely has two intentions to express — *"show me this ordering,
 * from the top"* and *"show me more of what I am reading"* — and the screen has
 * to keep them apart, because the first replaces the list and the second appends
 * to it. The wire has one slot for both, since YouTube models an ordering as a
 * continuation like any other.
 *
 * So they are folded here, at the edge, where the shapes are allowed to differ:
 * a continuation wins when there is one, because it already carries the ordering
 * it was handed out inside.
 *
 * A named function rather than an expression at the call site, so
 * [com.mytube.app.data.ChannelTokenTest] can hold it to the three cases without
 * a server: no order and no cursor, an order from the top, and a cursor inside
 * an order.
 */
internal fun channelToken(sortToken: String, pageToken: String): String =
    pageToken.ifEmpty { sortToken }
