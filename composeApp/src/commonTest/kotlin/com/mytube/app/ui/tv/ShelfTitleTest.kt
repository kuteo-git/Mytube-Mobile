package com.mytube.app.ui.tv

import com.mytube.app.domain.model.Topic
import com.mytube.app.ui.i18n.EnglishStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.i18n.VietnameseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/** A shelf's heading, in each language the app speaks. */
class ShelfTitleTest {

    private val languages: List<Strings> = listOf(EnglishStrings, VietnameseStrings)

    @Test
    fun `the fixed shelves are titled from the dictionary`() {
        for (strings in languages) {
            assertEquals(strings.continueWatching, shelfTitle(ShelfKind.ContinueWatching, strings))
            assertEquals(strings.chipMissed, shelfTitle(ShelfKind.Missed, strings))
            assertEquals(strings.chipLive, shelfTitle(ShelfKind.Live, strings))
            assertEquals(strings.shelfRecommended, shelfTitle(ShelfKind.Recommended, strings))
        }
    }

    /**
     * The words themselves, once per language, so a property wired to the
     * wrong shelf — Missed titled with the Live chip's word — reads as wrong
     * here rather than only on the screen.
     */
    @Test
    fun `the fixed shelves read as they should in English`() {
        assertEquals(
            listOf("Continue watching", "Missed", "Live", "Recommended"),
            fixed.map { shelfTitle(it, EnglishStrings) },
        )
    }

    @Test
    fun `the fixed shelves read as they should in Vietnamese`() {
        assertEquals(
            listOf("Xem tiếp", "Bỏ lỡ", "Live", "Đề xuất"),
            fixed.map { shelfTitle(it, VietnameseStrings) },
        )
    }

    /** The library's own label, the way a channel's sort names are upstream's. */
    @Test
    fun `a topic is titled by its own name, untranslated`() {
        val topic = ShelfKind.OfTopic(Topic("Âm nhạc", 12))
        for (strings in languages) {
            assertEquals("Âm nhạc", shelfTitle(topic, strings))
        }
        assertEquals("Music", shelfTitle(ShelfKind.OfTopic(Topic("Music", 3)), VietnameseStrings))
    }

    private val fixed = listOf(
        ShelfKind.ContinueWatching,
        ShelfKind.Missed,
        ShelfKind.Live,
        ShelfKind.Recommended,
    )
}
