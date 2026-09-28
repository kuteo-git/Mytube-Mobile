package com.mytube.app.data.repository

import com.mytube.app.data.remote.GatewayDataSource
import com.mytube.app.data.remote.dto.toDomain
import com.mytube.app.domain.model.StoryboardState
import com.mytube.app.domain.repository.ServerRepository
import com.mytube.app.domain.repository.StoryboardRepository

/**
 * Asks the gateway for one video's preview sheets.
 *
 * ## Why every failure is "no preview" rather than an error
 *
 * Because there is nothing a caller could usefully do with the difference, and
 * one thing it would certainly do wrong. A 404 means this video has no ladder,
 * which is most of the library; a refused connection means the server is not
 * answering, in which case the video is not playing either and the screen is
 * already saying so. Reporting either would put a second failure on a page that
 * has one, or a failure on a page where nothing is wrong.
 *
 * The cost is stated rather than hidden: a preview that fails for a reason worth
 * knowing fails silently, and the place that knows is the gateway's own log. That
 * is the same trade `loadComments` and `fillDescription` make, and it is only
 * acceptable because a bar with no still is a bar that works.
 */
class StoryboardRepositoryImpl(
    private val gateway: GatewayDataSource,
    private val server: ServerRepository,
) : StoryboardRepository {

    override suspend fun storyboard(videoId: String): StoryboardState {
        val base = server.baseUrl()
        if (base.isBlank()) return StoryboardState.None

        val board = runCatching {
            gateway.storyboard(base, server.profileId(), videoId).toDomain()
        }.getOrElse { return StoryboardState.None }

        // A board that arrived and cannot be drawn from is the same answer as no
        // board at all. Decided here so that a screen holding `Ready` can draw
        // without asking a second question about it.
        return if (board.isDrawable) StoryboardState.Ready(board) else StoryboardState.None
    }
}
