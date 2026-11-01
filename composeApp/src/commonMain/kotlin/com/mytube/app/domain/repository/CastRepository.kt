package com.mytube.app.domain.repository

import com.mytube.app.domain.model.CastAnswer
import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastHandback
import com.mytube.app.domain.model.CastSession

/** The household's speakers and the sessions on them, through the gateway. */
interface CastRepository {
    /** Speakers answering now; empty when none does or the server is away. */
    suspend fun devices(): List<CastDevice>

    /** Sessions running anywhere in the house. */
    suspend fun sessions(): CastAnswer<List<CastSession>>

    /** One session, ended ones included, so a remote can say why. */
    suspend fun session(id: String): CastAnswer<CastSession>

    suspend fun start(deviceId: String, videoIds: List<String>, startPositionSeconds: Int): CastAnswer<CastSession>
    suspend fun pause(id: String): CastAnswer<CastSession>
    suspend fun resume(id: String): CastAnswer<CastSession>
    suspend fun next(id: String): CastAnswer<CastSession>
    suspend fun stop(id: String): CastAnswer<CastSession>
    suspend fun seek(id: String, positionSeconds: Int): CastAnswer<CastSession>
    suspend fun volume(id: String, percent: Int): CastAnswer<CastSession>
    suspend fun jump(id: String, index: Int): CastAnswer<CastSession>
    suspend fun replaceQueue(id: String, version: Int, videoIds: List<String>, currentIndex: Int): CastAnswer<CastSession>
    suspend fun handoff(id: String): CastAnswer<CastHandback>
}
