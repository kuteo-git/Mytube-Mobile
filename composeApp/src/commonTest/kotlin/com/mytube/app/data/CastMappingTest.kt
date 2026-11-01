package com.mytube.app.data

import com.mytube.app.data.remote.dto.CastSessionDto
import com.mytube.app.data.remote.dto.toDomain
import com.mytube.app.data.repository.refusalFor
import com.mytube.app.domain.model.CastDeviceKind
import com.mytube.app.domain.model.CastEnd
import com.mytube.app.domain.model.CastNarration
import com.mytube.app.domain.model.CastRefusal
import com.mytube.app.domain.model.CastState
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CastMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    // The shape services/gateway/internal/api/cast.go writes (castSessionDTO),
    // as asserted by that file's TestStartingACastSendsTheProfileAndTheItems.
    private val running = """
        {"id":"s1","device":{"id":"a0cdbb43","name":"Kitchen speaker","kind":"google","online":true},
         "state":"playing","ended_reason":null,
         "queue":[{"video_id":"eq6lKYNgs8Y","title":"Motley Crew","channel":"Post Malone",
                   "thumbnail_url":"https://i.ytimg.com/vi/eq6lKYNgs8Y/hqdefault.jpg","duration_s":185}],
         "current_index":0,"position_s":176,"duration_s":184,"volume":0,"version":1,
         "started_by":"u_luc","updated_at":"2026-10-31T21:20:01+07:00"}
    """.trimIndent()

    @Test
    fun aRunningSessionIsReadWhole() {
        val s = json.decodeFromString<CastSessionDto>(running).toDomain()
        assertEquals(CastState.Playing, s.state)
        assertEquals(CastEnd.Running, s.end)
        assertEquals(CastDeviceKind.Google, s.device.kind)
        assertEquals("Kitchen speaker", s.device.name)
        assertEquals(176, s.positionSeconds)
        assertEquals("eq6lKYNgs8Y", s.current?.videoId)
        assertEquals(185, s.queue.single().durationSeconds)
    }

    @Test
    fun narrationOnTheSpeakerIsRead() {
        fun status(wire: String) = json.decodeFromString<CastSessionDto>(
            running.replace("\"version\":1,", "\"version\":1,\"narration\":true,\"narration_status\":\"$wire\","),
        ).toDomain().narration
        assertEquals(CastNarration.Preparing, status("preparing"))
        assertEquals(CastNarration.On, status("on"))
        assertEquals(CastNarration.Unavailable, status("unavailable"))
        assertEquals(CastNarration.Off, status("off"))
        assertEquals(CastNarration.Off, json.decodeFromString<CastSessionDto>(running).toDomain().narration,
            "a gateway from before narration on speakers")
    }

    @Test
    fun anEndedSessionCarriesItsReason() {
        val ended = running.replace("\"state\":\"playing\",\"ended_reason\":null", "\"state\":\"ended\",\"ended_reason\":\"taken_over\"")
        val s = json.decodeFromString<CastSessionDto>(ended).toDomain()
        assertEquals(CastState.Ended, s.state)
        assertEquals(CastEnd.TakenOver, s.end)
        assertEquals(true, s.end.needsSaying)
    }

    @Test
    fun anEndedSessionWithoutAKnownReasonStillEnds() {
        val s = json.decodeFromString<CastSessionDto>("""{"id":"s1","state":"ended","ended_reason":"something_new"}""").toDomain()
        assertEquals(CastEnd.Stopped, s.end)
        assertEquals(false, s.end.needsSaying)
    }

    @Test
    fun statusesBecomeReasons() {
        assertEquals(CastRefusal.StaleQueue, refusalFor(409, CastRefusal.StaleQueue))
        assertEquals(CastRefusal.Ended, refusalFor(409, CastRefusal.Ended))
        assertEquals(CastRefusal.Live, refusalFor(422, CastRefusal.Ended))
        assertEquals(CastRefusal.Offline, refusalFor(503, CastRefusal.Ended))
        assertEquals(CastRefusal.NotFound, refusalFor(404, CastRefusal.Ended))
        assertEquals(CastRefusal.Unreachable, refusalFor(500, CastRefusal.Ended))
    }
}
