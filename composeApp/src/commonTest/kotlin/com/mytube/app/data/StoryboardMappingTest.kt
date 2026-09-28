package com.mytube.app.data

import com.mytube.app.data.remote.dto.StoryboardDto
import com.mytube.app.data.remote.dto.toDomain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wire, turned into something a bar can draw from.
 *
 * The body below is a **real** answer from the running gateway, copied rather
 * than invented — `StreamMappingTest`'s rule, and it earns its place here for the
 * same reason: this route was written on both sides in one sitting, so a test
 * written against a guess would have proved the guess twice.
 */
class StoryboardMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun readsTheGatewaysOwnAnswer() {
        // GET /api/videos/gEWF0LL4IPA/storyboard, 2026-09-28. A portrait
        // upload, which is why the tile is narrow and 180 tall: the rungs are
        // named by height whatever the video's shape.
        val body = """
            {
              "tileWidth": 101,
              "tileHeight": 180,
              "rows": 3,
              "columns": 3,
              "intervalSeconds": 1.9583333333333333,
              "sprites": [
                "gEWF0LL4IPA/storyboard/0.webp",
                "gEWF0LL4IPA/storyboard/1.webp",
                "gEWF0LL4IPA/storyboard/2.webp",
                "gEWF0LL4IPA/storyboard/3.webp",
                "gEWF0LL4IPA/storyboard/4.webp",
                "gEWF0LL4IPA/storyboard/5.webp",
                "gEWF0LL4IPA/storyboard/6.webp",
                "gEWF0LL4IPA/storyboard/7.webp"
              ]
            }
        """.trimIndent()

        val board = json.decodeFromString<StoryboardDto>(body).toDomain()

        assertEquals(101, board.tileWidth)
        assertEquals(180, board.tileHeight)
        assertEquals(3, board.rows)
        assertEquals(3, board.columns)
        assertEquals(1.9583333333333333, board.intervalSeconds)
        assertEquals(8, board.sprites.size)
        assertTrue(board.isDrawable)

        // The interval is a sheet's duration over its slots, not the video's
        // length over every slot. This video is 141s with 72 slots, which would
        // give 1.958 as well — the two agree here, which is exactly why the
        // mistake survived being looked at. What separates them is a video whose
        // last sheet is part empty, and `StoryboardTest` holds that case.
        assertEquals(1.9583333333333333, board.intervalSeconds)
    }

    @Test
    fun anEmptyBodyIsABoardNobodyCanDrawFrom() {
        // Not a parse failure: §3 puts absence on the DTO and decides it once, in
        // the mapper. Every field defaulting means a truncated or future answer
        // arrives as "no preview" rather than as an exception over a working
        // video.
        val board = json.decodeFromString<StoryboardDto>("{}").toDomain()
        assertTrue(!board.isDrawable)
    }

    @Test
    fun aBodyMissingOnlyTheSheetsIsAlsoUndrawable() {
        val body = """{"tileWidth":320,"tileHeight":180,"rows":3,"columns":3,"intervalSeconds":4.96}"""
        assertTrue(!json.decodeFromString<StoryboardDto>(body).toDomain().isDrawable)
    }
}
