package com.mytube.app.domain

import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.clipAt
import com.mytube.app.domain.model.levelsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The two decisions narration makes, tested away from an audio clock.
 *
 * Which line is due, and how loud the two sounds are. Everything else about the
 * feature is a file arriving or not arriving, and that is measured on a device —
 * the emulator this was built against runs with no audio at all, so ducking
 * cannot be heard here and is not claimed to have been.
 */
class NarrationTest {

    private fun clip(start: Double, seconds: Double) =
        NarrationClip(start, seconds, "http://x/$start.wav", "line at $start")

    @Test
    fun findsTheLineThatIsDue() {
        val clips = listOf(clip(0.0, 2.0), clip(5.0, 1.5))
        assertEquals(0.0, clipAt(clips, 1.0)?.startSeconds)
        assertEquals(5.0, clipAt(clips, 5.4)?.startSeconds)
    }

    @Test
    fun saysNothingInTheGaps() {
        val clips = listOf(clip(0.0, 2.0), clip(5.0, 1.5))
        // Between lines, and past the end. Both must be silent rather than
        // holding the last clip, or the video stays ducked for the rest of it.
        assertNull(clipAt(clips, 3.0))
        assertNull(clipAt(clips, 99.0))
    }

    @Test
    fun aLineEndsWhenItsSlotDoes() {
        // Exclusive at the end: at exactly 2.0 the first line is over. Inclusive
        // would leave two clips claiming the same instant where they touch.
        val clips = listOf(clip(0.0, 2.0), clip(2.0, 2.0))
        assertEquals(2.0, clipAt(clips, 2.0)?.startSeconds)
    }

    @Test
    fun duckingIsAFractionOfMasterOnBothSides() {
        // The bug this shape exists to prevent: the two used to be chained, so
        // the voice took its gain from the already-ducked video and moving one
        // level dragged the other.
        val levels = levelsFor(master = 0.8f, muted = false, narrating = true)
        assertEquals(0.8f * 0.5f, levels.narration)
        assertEquals(0.8f * 0.2f, levels.video)
    }

    @Test
    fun theVideoIsAtFullLevelWhenNobodyIsSpeaking() {
        val levels = levelsFor(master = 0.8f, muted = false, narrating = false)
        assertEquals(0f, levels.narration)
        assertEquals(0.8f, levels.video)
    }

    @Test
    fun mutedSilencesBoth() {
        // Not just the video. A mute that left the voice talking would be the
        // worst possible reading of the button.
        val levels = levelsFor(master = 1f, muted = true, narrating = true)
        assertEquals(0f, levels.narration)
        assertEquals(0f, levels.video)
    }

    @Test
    fun anUnknownStatusReadsAsIdle() {
        // A word a later server release invents must not be drawn as a failure.
        assertEquals(NarrationStatus.Idle, NarrationStatus.fromWire("preparing"))
        assertEquals(NarrationStatus.Running, NarrationStatus.fromWire("running"))
    }
}
