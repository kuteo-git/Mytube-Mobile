package com.mytube.app.data.repository

import com.mytube.app.data.remote.GatewayDataSource
import com.mytube.app.data.remote.dto.toDomain
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.repository.ServerRepository
import com.mytube.app.domain.repository.StreamRepository

class StreamRepositoryImpl(
    private val gateway: GatewayDataSource,
    private val server: ServerRepository,
) : StreamRepository {

    override suspend fun stream(videoId: String, maxHeight: Int): Stream {
        val baseUrl = server.baseUrl().ifBlank { throw ServerNotConfigured() }
        return gateway.stream(baseUrl, server.profileId(), videoId)
            .toDomain(baseUrl, maxHeight)
    }
}
