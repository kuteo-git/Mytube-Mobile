package com.mytube.app.data.repository

import com.mytube.app.data.remote.GatewayDataSource
import com.mytube.app.data.remote.dto.toDomain
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.repository.ServerRepository
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

    override suspend fun video(id: String): Video =
        gateway.video(requireBaseUrl(), server.profileId(), id).toDomain()

    override suspend fun search(query: String): List<Video> =
        gateway.search(requireBaseUrl(), server.profileId(), query).map { it.toDomain() }

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
    override suspend fun history(): List<Video> =
        gateway.history(requireBaseUrl(), server.profileId(), limit = 24)
            .videos.map { it.toDomain() }

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
