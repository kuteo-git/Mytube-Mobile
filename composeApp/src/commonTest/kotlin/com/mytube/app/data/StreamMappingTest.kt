package com.mytube.app.data

import com.mytube.app.data.remote.dto.StreamDto
import com.mytube.app.data.remote.dto.toDomain
import com.mytube.app.domain.model.Stream
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which tier gets played, and the fault this exists to stop happening a third
 * time.
 *
 * Twice now a tier has been declared in [StreamDto] and never read in
 * `toDomain`: `live` first, and then `local`. Both fell through to
 * `NothingPlayable`, which the watch screen turns into *"YouTube will not hand
 * this video over"* — a sentence that names an upstream refusal, offers no
 * retry, and in the `local` case was said about a file sitting on the
 * household's own disk.
 *
 * Nothing in the type system catches a `when` branch that was never written, so
 * this does. Every JSON body below is a **real** answer from the gateway, copied
 * from the running server rather than invented, because a test written against a
 * guess proves the guess.
 */
class StreamMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun stream(body: String): Stream =
        json.decodeFromString<StreamDto>(body).toDomain("http://10.0.0.2:8180", maxHeight = 720)

    @Test
    fun playsTheHlsLadderAndCarriesTheDeviceCeiling() {
        val playable = stream(
            """
            {"cacheDisabled":true,
             "hls":{"url":"/api/videos/sZrIbpwjTwk/hls/master.m3u8","height":720,
                    "mimeType":"application/vnd.apple.mpegurl","seekable":true},
             "remux":{"url":"/api/videos/sZrIbpwjTwk/remux","height":720,
                      "mimeType":"video/mp4","seekable":false}}
            """.trimIndent(),
        )

        assertTrue(playable is Stream.Playable)
        assertEquals(
            "http://10.0.0.2:8180/api/videos/sZrIbpwjTwk/hls/master.m3u8?max=720",
            playable.url,
        )
        assertEquals(false, playable.isLive)
    }

    /**
     * A downloaded video, and the whole reason this file exists.
     *
     * `mediaState READY` on the server means the file is on disk, and the
     * gateway then answers with **this tier alone** — no `hls` key at all. Before
     * the `local` branch existed, this body produced `NothingPlayable`.
     */
    @Test
    fun playsTheFileOnDiskWhenThatIsAllTheServerOffers() {
        val playable = stream(
            """{"local":{"url":"/media/pYTHEpMod8E/1080p.mp4","mimeType":"video/mp4","seekable":true}}""",
        )

        assertTrue(playable is Stream.Playable)
        assertEquals("http://10.0.0.2:8180/media/pYTHEpMod8E/1080p.mp4", playable.url)
        // No `?max=`: there is no ladder to cap, only a file.
        assertTrue(!playable.url.contains("max="))
        assertEquals(false, playable.isLive)
    }

    /**
     * A broadcast answers with the live tier and nothing else, and it must not
     * carry the ceiling — that parameter is read where the *ordinary* master
     * playlist is written, and the live master comes from another route.
     */
    @Test
    fun playsALiveMasterWithoutTheCeiling() {
        val playable = stream(
            """{"live":{"url":"/api/videos/abc/live/master.m3u8","height":720}}""",
        )

        assertTrue(playable is Stream.Playable)
        assertEquals("http://10.0.0.2:8180/api/videos/abc/live/master.m3u8", playable.url)
        assertTrue(playable.isLive)
    }

    /**
     * The one case the message is honest about. An answer with no tier at all is
     * a video the server could not resolve, and saying so is right — it is
     * saying it about a downloaded file that was the fault.
     */
    @Test
    fun nothingPlayableOnlyWhenNothingIsOffered() {
        assertTrue(stream("""{"cacheDisabled":true}""") is Stream.NothingPlayable)
    }

    @Test
    fun upcomingAndUnavailableComeBeforeEveryTier() {
        assertTrue(stream("""{"upcoming":true}""") is Stream.Upcoming)

        val refused = stream("""{"unavailable":true,"reason":"members_only"}""")
        assertTrue(refused is Stream.Unavailable)
        assertEquals("members_only", refused.reason)
    }
}
