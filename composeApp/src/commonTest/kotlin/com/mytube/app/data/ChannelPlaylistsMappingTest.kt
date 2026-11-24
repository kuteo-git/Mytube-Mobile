package com.mytube.app.data

import com.mytube.app.data.remote.dto.ChannelPlaylistVideosDto
import com.mytube.app.data.remote.dto.ChannelPlaylistsDto
import com.mytube.app.data.remote.dto.toDomain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the channel-playlists routes send, and what the app makes of it.
 *
 * The bodies mirror the gateway's `channelPlaylistDTO` and `externalVideoDTO`
 * wire tags exactly, the shapes asserted in the gateway's own
 * `channel_playlists_test.go` — copied rather than invented, per the rule a
 * declared-but-unread tier shipped three times before teaching.
 */
class ChannelPlaylistsMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** `GET /api/channels/{id}/playlists`, as the gateway answers it. */
    @Test
    fun channelPlaylistsCrossTheWire() {
        val dto = json.decodeFromString<ChannelPlaylistsDto>(
            """
            {"playlists":[
              {"id":"PLBsP89CPrMeMV1oP42Zn2DrWpnFoSaufA","title":"Retro Tech",
               "thumbnailUrl":"https://i9.ytimg.com/s_p/PL.../maxres.jpg","videoCount":6},
              {"id":"PLBsP89CPrMeNFZ6dB0u604cghlzp2GsMS","title":"Live Streams!",
               "thumbnailUrl":"https://i9.ytimg.com/s_p/LV.../maxres.jpg","videoCount":0}
            ],"nextPageToken":"ndx"}
            """.trimIndent(),
        )
        val playlists = dto.playlists.map { it.toDomain() }

        assertEquals(2, playlists.size)
        val retro = playlists.first()
        assertEquals("PLBsP89CPrMeMV1oP42Zn2DrWpnFoSaufA", retro.id)
        assertEquals("Retro Tech", retro.title)
        assertEquals(6, retro.videoCount)
        assertTrue(retro.hasCount)
        assertTrue(retro.hasThumbnail)
        // A count the server did not state is zero, read through hasCount rather
        // than printed as "0 videos".
        assertFalse(playlists[1].hasCount)
        assertEquals("ndx", dto.nextPageToken)
    }

    /** A sparse card must parse rather than fail: §3, absence decided in the mapper. */
    @Test
    fun aSparsePlaylistStillParses() {
        val only = json.decodeFromString<ChannelPlaylistsDto>(
            """{"playlists":[{"id":"PL_1","title":"Tin tức"}]}""",
        ).playlists.single().toDomain()

        assertEquals("", only.thumbnailUrl)
        assertFalse(only.hasThumbnail)
        assertEquals(0, only.videoCount)
        assertFalse(only.hasCount)
    }

    /**
     * `GET /api/channels/{id}/playlists/{playlistId}/videos` — a playlist mixes
     * channels, so each row must carry its own uploader, not the channel the
     * page was opened from.
     */
    @Test
    fun openingAPlaylistKeepsEachVideosOwnUploader() {
        val dto = json.decodeFromString<ChannelPlaylistVideosDto>(
            """
            {"title":"Retro Tech","videos":[
              {"id":"kMtN9KJHn5Y","title":"Game Boy","channelName":"Marques Brownlee",
               "durationSeconds":769,"viewCount":9300000,
               "thumbnailUrl":"https://i.ytimg.com/vi/kMtN9KJHn5Y/hq720.jpg",
               "sourceUrl":"https://www.youtube.com/watch?v=kMtN9KJHn5Y",
               "publishedAt":"2020-01-01T00:00:00Z","inLibrary":false}
            ],"nextPageToken":"vtk"}
            """.trimIndent(),
        )

        assertEquals("Retro Tech", dto.title)
        val video = dto.videos.single().toDomain()
        assertEquals("kMtN9KJHn5Y", video.id)
        // The uploader, not the owning channel — the honest label on a mixed list.
        assertEquals("Marques Brownlee", video.channel.name)
        assertEquals(9_300_000, video.viewCount)
        // The two facts that make an upstream row openable travel through.
        assertEquals("https://www.youtube.com/watch?v=kMtN9KJHn5Y", video.sourceUrl)
        assertFalse(video.inLibrary)
        assertEquals("vtk", dto.nextPageToken)
    }

    /** A continuation page carries no header; the title comes back empty. */
    @Test
    fun aContinuationPageHasNoTitle() {
        val dto = json.decodeFromString<ChannelPlaylistVideosDto>(
            """{"videos":[],"nextPageToken":"more"}""",
        )
        assertEquals("", dto.title)
    }
}
