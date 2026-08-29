package com.mytube.app.ui.watch

import com.mytube.app.domain.model.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackLabelTest {

    private fun track(language: String, generated: Boolean) =
        SubtitleTrack(language, "ignored", "/media/x.vtt", generated)

    @Test
    fun namesTheLanguage() {
        assertEquals("EN", trackLabel(track("en", generated = false)))
    }

    @Test
    fun marksAMachineTrack() {
        assertEquals("EN (auto)", trackLabel(track("en", generated = true)))
    }

    @Test
    fun dropsThePrivateSubtag() {
        // The machine track is tagged vi-x-mt so nothing confuses it with the
        // human Vietnamese track on disk. That is a storage distinction, and
        // printing it raw gave a chip reading "VI-X-MT (auto)".
        assertEquals("VI (auto)", trackLabel(track("vi-x-mt", generated = true)))
    }
}
