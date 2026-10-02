package com.mytube.app.ui.watch

import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.ui.i18n.EnglishStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackLabelTest {

    private fun track(language: String, generated: Boolean) =
        SubtitleTrack(language, "ignored", "/media/x.vtt", generated)

    @Test
    fun namesTheLanguage() {
        assertEquals("EN", trackLabel(track("en", generated = false), EnglishStrings))
    }

    @Test
    fun marksAMachineTrack() {
        assertEquals("EN (auto)", trackLabel(track("en", generated = true), EnglishStrings))
    }

    @Test
    fun dropsThePrivateSubtag() {
        // The machine track is tagged vi-x-mt so nothing confuses it with the
        // human Vietnamese track on disk. That is a storage distinction, and
        // printing it raw gave a chip reading "VI-X-MT (auto)".
        assertEquals("VI (auto)", trackLabel(track("vi-x-mt", generated = true), EnglishStrings))
    }

    /**
     * The machine mark is copy, so it is the language's.
     *
     * It was a literal in the formatter, and a Vietnamese viewer read "(auto)"
     * beside "Tắt" — the half-translation §7 exists to make impossible, in the
     * one place the guard cannot see because it is not inside a composable.
     */
    @Test
    fun marksAMachineTrackInTheViewersLanguage() {
        assertEquals("VI (tự động)", trackLabel(track("vi-x-mt", generated = true), VietnameseStrings))
    }
}
