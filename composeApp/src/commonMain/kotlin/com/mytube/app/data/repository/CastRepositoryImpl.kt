package com.mytube.app.data.repository

import com.mytube.app.data.remote.CastCommandBody
import com.mytube.app.data.remote.CastIdBody
import com.mytube.app.data.remote.CastQueueBody
import com.mytube.app.data.remote.GatewayDataSource
import com.mytube.app.data.remote.GatewayException
import com.mytube.app.data.remote.StartCastBody
import com.mytube.app.data.remote.dto.CastSessionDto
import com.mytube.app.data.remote.dto.toDomain
import com.mytube.app.domain.model.CastAnswer
import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastHandback
import com.mytube.app.domain.model.CastRefusal
import com.mytube.app.domain.model.CastSession
import com.mytube.app.domain.repository.CastRepository
import com.mytube.app.domain.repository.ServerRepository
import kotlinx.coroutines.CancellationException

class CastRepositoryImpl(
    private val gateway: GatewayDataSource,
    private val server: ServerRepository,
) : CastRepository {

    override suspend fun devices(): List<CastDevice> =
        when (val answer = ask { gateway.castDevices(server.baseUrl(), server.profileId()).map { it.toDomain() } }) {
            is CastAnswer.Done -> answer.value
            is CastAnswer.Refused -> emptyList()
        }

    override suspend fun sessions(): CastAnswer<List<CastSession>> =
        ask { gateway.castSessions(server.baseUrl(), server.profileId()).map { it.toDomain() } }

    override suspend fun session(id: String): CastAnswer<CastSession> =
        session { gateway.castSession(server.baseUrl(), server.profileId(), id) }

    override suspend fun start(deviceId: String, videoIds: List<String>, startPositionSeconds: Int) = session {
        gateway.startCast(
            server.baseUrl(), server.profileId(),
            StartCastBody(deviceId, videoIds.map(::CastIdBody), startIndex = 0, startPositionSeconds.coerceAtLeast(0)),
        )
    }

    override suspend fun pause(id: String) = command(id, "pause")
    override suspend fun resume(id: String) = command(id, "resume")
    override suspend fun next(id: String) = command(id, "next")
    override suspend fun stop(id: String) = command(id, "stop")
    override suspend fun seek(id: String, positionSeconds: Int) =
        command(id, "seek", CastCommandBody(positionSeconds = positionSeconds.coerceAtLeast(0)))
    override suspend fun volume(id: String, percent: Int) =
        command(id, "volume", CastCommandBody(percent = percent.coerceIn(0, 100)))
    override suspend fun jump(id: String, index: Int) = command(id, "jump", CastCommandBody(index = index))

    override suspend fun replaceQueue(id: String, version: Int, videoIds: List<String>, currentIndex: Int) =
        session(conflict = CastRefusal.StaleQueue) {
            gateway.replaceCastQueue(
                server.baseUrl(), server.profileId(), id,
                CastQueueBody(version, videoIds.map(::CastIdBody), currentIndex),
            )
        }

    override suspend fun handoff(id: String): CastAnswer<CastHandback> =
        ask { gateway.castHandoff(server.baseUrl(), server.profileId(), id).toDomain() }

    private suspend fun command(id: String, action: String, body: CastCommandBody? = null) =
        session { gateway.castCommand(server.baseUrl(), server.profileId(), id, action, body) }

    private suspend fun session(
        conflict: CastRefusal = CastRefusal.Ended,
        call: suspend () -> CastSessionDto,
    ): CastAnswer<CastSession> = ask(conflict) { call().toDomain() }

    private suspend fun <T> ask(conflict: CastRefusal = CastRefusal.Ended, call: suspend () -> T): CastAnswer<T> {
        if (server.baseUrl().isBlank()) return CastAnswer.Refused(CastRefusal.Unreachable)
        return try {
            CastAnswer.Done(call())
        } catch (e: CancellationException) {
            throw e
        } catch (e: GatewayException) {
            CastAnswer.Refused(refusalFor(e.status, conflict))
        } catch (e: Exception) {
            CastAnswer.Refused(CastRefusal.Unreachable)
        }
    }
}

/**
 * The gateway's status, as a reason a screen can word. A conflict means a stale
 * queue for a queue edit and an ended session for anything else; the gateway
 * says which in the body, and the call site already knows.
 */
internal fun refusalFor(status: Int, conflict: CastRefusal): CastRefusal = when (status) {
    404 -> CastRefusal.NotFound
    409 -> conflict
    422 -> CastRefusal.Live
    503 -> CastRefusal.Offline
    else -> CastRefusal.Unreachable
}
