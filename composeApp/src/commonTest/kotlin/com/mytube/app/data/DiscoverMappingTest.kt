package com.mytube.app.data

import com.mytube.app.data.remote.dto.DiscoverDto
import com.mytube.app.data.remote.dto.ExternalVideoDto
import com.mytube.app.data.remote.dto.toDomain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What `GET /api/discover` sends, and what the app makes of it.
 *
 * The bodies below are **real** answers from the running gateway, copied rather
 * than invented — the rule `StreamMappingTest` was written under, after a field
 * declared on a DTO and never read shipped twice.
 */
class DiscoverMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** One page as the server actually answered it, for "phuong my chi". */
    private val body = """
        {"videos":[
          {"id":"UH21OnJwxZE",
           "title":"PHƯƠNG MỸ CHI x DTAP | 'THIÊN ĐƯỜNG VỚI NGƯỜI THƯƠNG' | OFFICIAL MUSIC VIDEO",
           "channelName":"Phương Mỹ Chi and DTAP","durationSeconds":247,
           "viewCount":20954665,
           "thumbnailUrl":"https://i.ytimg.com/vi/UH21OnJwxZE/hq720.jpg",
           "sourceUrl":"https://www.youtube.com/watch?v=UH21OnJwxZE","inLibrary":true},
          {"id":"4npRiky2Ukg",
           "title":"PHƯƠNG MỸ CHI x DTAP | THỬ LÒNG QUÂN TỬ (ft. RHYDER) | OFFICIAL VISUALIZER",
           "channelName":"Phương Mỹ Chi and 2 more","durationSeconds":204,
           "viewCount":510387,
           "thumbnailUrl":"https://i.ytimg.com/vi/4npRiky2Ukg/hq720.jpg",
           "sourceUrl":"https://www.youtube.com/watch?v=4npRiky2Ukg","inLibrary":true}
        ]}
    """.trimIndent()

    @Test
    fun readsAPageTheServerActuallySent() {
        val videos = json.decodeFromString<DiscoverDto>(body).videos.map { it.toDomain() }

        assertEquals(2, videos.size)
        val first = videos.first()
        assertEquals("UH21OnJwxZE", first.id)
        assertEquals("Phương Mỹ Chi and DTAP", first.channelName)
        assertEquals(247, first.durationSeconds)
        assertEquals(20_954_665, first.viewCount)
        assertTrue(first.inLibrary)
        // Absolute, and pointing at YouTube. Nothing here is resolved against
        // the server's address the way a library thumbnail is.
        assertTrue(first.thumbnailUrl.startsWith("https://i.ytimg.com/"))
        // The address, not an id turned into one by this app.
        assertEquals("https://www.youtube.com/watch?v=UH21OnJwxZE", first.sourceUrl)
    }

    /**
     * An empty answer is an answer.
     *
     * The gateway returns `{"videos":[]}` for a blank query, for a pasted
     * address naming no video, and for one whose video the library already
     * holds. None of those is a failure and none may throw.
     */
    @Test
    fun anEmptyPageIsNotAFailure() {
        val videos = json.decodeFromString<DiscoverDto>("""{"videos":[]}""").videos
        assertTrue(videos.isEmpty())
    }

    /**
     * A body with fields missing still parses.
     *
     * Every field on the DTO is defaulted for this reason: absence is real on
     * the wire — a video with no view count comes back without the key — and
     * turning a legal answer into a parse failure is the worst way to handle it.
     */
    @Test
    fun aMissingFieldIsAbsent() {
        val dto = json.decodeFromString<ExternalVideoDto>("""{"id":"x","title":"A video"}""")
        val video = dto.toDomain()

        assertEquals("x", video.id)
        assertEquals(0, video.durationSeconds)
        assertEquals(0, video.viewCount)
        assertEquals("", video.channelName)
        assertFalse(video.inLibrary)
    }
}
