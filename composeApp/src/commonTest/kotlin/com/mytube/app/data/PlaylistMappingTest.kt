package com.mytube.app.data

import com.mytube.app.data.remote.dto.PlaylistPageDto
import com.mytube.app.data.remote.dto.PlaylistsDto
import com.mytube.app.data.remote.dto.toDomain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the playlist routes send, and what the app makes of it.
 *
 * The bodies below are **real** answers from the running gateway, copied rather
 * than invented — the rule `StreamMappingTest` was written under, after a tier
 * declared on a DTO and never read shipped twice and then a third time.
 */
class PlaylistMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** `GET /api/playlists?videoId=gEWF0LL4IPA`, exactly as answered. */
    private val listBody = """
        {"playlists":[
          {"id":"33a3e35e-3e74-403f-afce-7033c4e7e91a","title":"Nhạc","description":"",
           "itemCount":1,"updatedAt":"2026-09-01T15:36:13Z","itemsSynced":false,
           "unavailable":false,"thumbnails":["thumbnails/gEWF0LL4IPA.jpg"],
           "containsVideo":true}
        ]}
    """.trimIndent()

    @Test
    fun membershipCrossesTheWire() {
        val playlists = json.decodeFromString<PlaylistsDto>(listBody).playlists.map { it.toDomain() }

        assertEquals(1, playlists.size)
        val nhac = playlists.first()
        assertEquals("Nhạc", nhac.title)
        assertEquals(1, nhac.itemCount)
        // The whole reason `?videoId=` exists: the sheet's ticks come from this
        // flag, in the same call that fetched the rows they sit on.
        assertTrue(nhac.containsVideo, "containsVideo did not reach the domain")
        assertEquals(listOf("thumbnails/gEWF0LL4IPA.jpg"), nhac.thumbnailPaths)
    }

    /**
     * The same route asked without a video — which is every other caller.
     *
     * The flag then says nothing, and it must say nothing *false*: a playlists
     * page that ticked rows would be claiming membership nobody asked about.
     */
    @Test
    fun withoutTheParameterNothingClaimsMembership() {
        val body = listBody.replace("\"containsVideo\":true", "\"containsVideo\":false")
        val playlists = json.decodeFromString<PlaylistsDto>(body).playlists.map { it.toDomain() }

        assertFalse(playlists.first().containsVideo)
    }

    /**
     * A body with fields missing.
     *
     * Not hypothetical: `description` is omitted by nothing today and could be
     * tomorrow, and §3's rule is that absence is decided once, in the mapper.
     * A parse failure is the worst possible answer to a legal response.
     */
    @Test
    fun aSparseBodyStillParses() {
        val playlists = json.decodeFromString<PlaylistsDto>(
            """{"playlists":[{"id":"pl_1","title":"Tin tức"}]}""",
        ).playlists.map { it.toDomain() }

        val only = playlists.single()
        assertEquals("", only.description)
        assertEquals(0, only.itemCount)
        assertFalse(only.containsVideo)
        assertFalse(only.hasThumbnail)
    }

    /** `GET /api/playlists/{id}`, trimmed to the fields this app reads. */
    @Test
    fun aPageCarriesItsPlaylistAndItsVideos() {
        val page = json.decodeFromString<PlaylistPageDto>(
            """
            {"playlist":{"id":"33a3e35e-3e74-403f-afce-7033c4e7e91a","title":"Nhạc",
              "description":"","itemCount":1,"updatedAt":"2026-09-01T15:36:13Z",
              "itemsSynced":false,"unavailable":false,
              "thumbnails":["thumbnails/gEWF0LL4IPA.jpg"],"containsVideo":false},
             "videos":[{"id":"gEWF0LL4IPA","title":"Lên ngay Nothing Phone (4a) Pro"}],
             "nextPageToken":""}
            """.trimIndent(),
        )

        assertEquals("Nhạc", page.playlist.toDomain().title)
        assertEquals("gEWF0LL4IPA", page.videos.single().toDomain().id)
        // Empty rather than absent, and the domain asks `hasMore` rather than
        // testing the string — see `FeedPage`.
        assertEquals("", page.nextPageToken)
    }
}
