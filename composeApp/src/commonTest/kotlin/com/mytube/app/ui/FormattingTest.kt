package com.mytube.app.ui

import com.mytube.app.ui.home.formatCount
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.home.formatViews
import com.mytube.app.ui.i18n.EnglishStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two numbers on every card.
 *
 * Pure functions, so they are tested without a screen — which is the point of
 * their being pure. The server charter records what the web app's equivalents
 * cost when they were not: its formatters carried English grammar into a
 * language with no plurals and produced "3 ngàys trước" on every card.
 */
class FormattingTest {

    @Test
    fun durationBelowAnHourHasNoHourPart() {
        assertEquals("0:07", formatDuration(7))
        assertEquals("1:00", formatDuration(60))
        assertEquals("7:31", formatDuration(451))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test
    fun pastAnHourTheMinutesArePadded() {
        // 1:02:03, not 1:2:3 — and this is the case a naive implementation gets
        // wrong, because minutes are unpadded below an hour and padded above it.
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:02:03", formatDuration(3723))
        assertEquals("3:00:01", formatDuration(10801))
    }

    @Test
    fun zeroIsStillAValidDuration() {
        // The card hides the badge at zero rather than the formatter refusing;
        // a live video reports 0 and must not crash the row it sits in.
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun viewsAreAbbreviatedTheWayAFeedSaysThem() {
        val en = EnglishStrings
        assertEquals("0 views", formatViews(0, en))
        assertEquals("1 views", formatViews(1, en))
        assertEquals("999 views", formatViews(999, en))
        assertEquals("1K views", formatViews(1_000, en))
        assertEquals("157K views", formatViews(157_000, en))
        assertEquals("1.2M views", formatViews(1_234_567, en))
        assertEquals("4.7M views", formatViews(4_730_000, en))
        assertEquals("3B views", formatViews(3_000_000_000, en))
    }

    /**
     * The same numbers in Vietnamese.
     *
     * These are the values the web app produces, asserted here so the two
     * clients abbreviate a view count identically. A household member switching
     * between the phone and the browser should not see 4.1M in one and something
     * else in the other.
     */
    @Test
    fun vietnameseUsesItsOwnScaleWords() {
        val vi = VietnameseStrings
        assertEquals("2.4N", formatCount(2_400, vi))
        assertEquals("4.1Tr", formatCount(4_100_000, vi))
        assertEquals("3T", formatCount(3_000_000_000, vi))
        assertEquals("157N lượt xem", formatViews(157_000, vi))
    }

    @Test
    fun oneDecimalBelowAHundredAndNoneAboveIt() {
        // The web app's rounding rule, copied rather than reinvented: 4.1M but
        // 157K, never 157.0K.
        val en = EnglishStrings
        assertEquals("1.2M", formatCount(1_234_567, en))
        assertEquals("157K", formatCount(157_000, en))
        assertEquals("999K", formatCount(999_400, en))
        // Trailing .0 is dropped rather than printed.
        assertEquals("2M", formatCount(2_000_000, en))
    }
}
