package com.mytube.app.data

import com.mytube.app.data.remote.dto.FeedDto
import com.mytube.app.data.remote.dto.VideoDto
import com.mytube.app.data.remote.dto.toDomain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The boundary where the wire's nulls stop.
 *
 * The rule for this project is that `domain` and ViewModels carry nothing
 * nullable, and DTOs carry everything the wire may omit. That makes this mapper
 * the single place absence is decided, and therefore the single place worth
 * testing for it.
 *
 * The JSON below is copied from a real `/api/feed` response rather than invented,
 * because a test written against a guess proves the guess.
 */
class VideoMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun readsARealFeedResponse() {
        val body = """
            {
              "videos": [
                {
                  "id": "LkVLxE0B7P8",
                  "title": "Canadian describes what he saw as catastrophic floods hit Nepal",
                  "channel": {
                    "id": "UCuFFtHWoLl5fauMMD5Ww2jA",
                    "name": "CBC News",
                    "handle": "@CBCNews",
                    "avatarPath": "channels/UCuFFtHWoLl5fauMMD5Ww2jA/avatar.jpg",
                    "bannerPath": "",
                    "subscriberCount": 4730000,
                    "verified": false,
                    "subscribed": true
                  },
                  "durationSeconds": 451,
                  "viewCount": 157000,
                  "publishedAt": "2026-08-28T05:35:52Z",
                  "addedAt": "2026-08-28T01:10:48Z",
                  "thumbnailPath": "thumbnails/LkVLxE0B7P8.jpg",
                  "mediaState": "ABSENT",
                  "pinned": false,
                  "sourceUrl": "https://www.youtube.com/watch?v=LkVLxE0B7P8",
                  "reason": "SUBSCRIBED_CHANNEL",
                  "isLiveNow": false
                }
              ],
              "nextPageToken": "abc"
            }
        """.trimIndent()

        val video = json.decodeFromString<FeedDto>(body).videos.single().toDomain()

        assertEquals("LkVLxE0B7P8", video.id)
        assertEquals(451, video.durationSeconds)
        assertEquals("CBC News", video.channel.name)
        assertTrue(video.channel.subscribed)
        assertTrue(video.hasPublishedDate)
    }

    @Test
    fun ignoresFieldsTheAppDoesNotRead() {
        // The response above carries mediaState, sourceUrl, reason, isLiveNow,
        // addedAt and more. None is modelled, and a server release adding another
        // must not stop the app parsing — which is what `ignoreUnknownKeys` is
        // for, asserted rather than assumed.
        val video = json.decodeFromString<VideoDto>("""{"id":"x","somethingNew":42}""")
        assertEquals("x", video.id)
    }

    @Test
    fun aMissingDateBecomesEmptyRatherThanNull() {
        // 1,127 videos reached that catalogue with no publish date, so this is
        // the ordinary case and not an edge one.
        val video = json.decodeFromString<VideoDto>("""{"id":"x"}""").toDomain()

        assertEquals("", video.publishedAt)
        assertFalse(video.hasPublishedDate)
    }

    @Test
    fun theServersPinnedIsThisViewersSaved() {
        // The two words mean different things on the server — Save is a personal
        // shelf, pinning is a fact about one disk — and it settled on sending the
        // viewer's own answer under the older name. The rename happens here so
        // nothing downstream inherits the confusion.
        val video = json.decodeFromString<VideoDto>("""{"id":"x","pinned":true}""").toDomain()
        assertTrue(video.saved)
    }

    @Test
    fun watchProgressSurvivesAndIsZeroWhenAbsent() {
        val watched = json.decodeFromString<VideoDto>(
            """{"id":"x","userState":{"watchProgress":0.4}}""",
        ).toDomain()
        assertEquals(0.4, watched.watchedFraction)

        val fresh = json.decodeFromString<VideoDto>("""{"id":"x"}""").toDomain()
        assertEquals(0.0, fresh.watchedFraction)
    }
}
