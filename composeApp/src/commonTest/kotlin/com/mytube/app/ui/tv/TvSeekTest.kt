package com.mytube.app.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Left and right on the remote: a run of presses moving a target, sent to the
 * player once the remote goes quiet.
 */
class TvSeekTest {

    // --- how far one press goes ----------------------------------------------

    /** A tap or a few is the phone's double-tap: ten seconds. */
    @Test
    fun `the first six presses of a run move ten seconds each`() {
        assertEquals(10.0, tvSeekStep(0))
        assertEquals(10.0, tvSeekStep(5))
    }

    /** Held, it climbs: crossing a film ten seconds at a time is hundreds of presses. */
    @Test
    fun `from the seventh press a step is thirty seconds`() {
        assertEquals(30.0, tvSeekStep(6))
        assertEquals(30.0, tvSeekStep(19))
    }

    @Test
    fun `from the twenty-first press a step is a minute`() {
        assertEquals(60.0, tvSeekStep(20))
        assertEquals(60.0, tvSeekStep(500))
    }

    // --- where the target lands ----------------------------------------------

    @Test
    fun `a press moves the target by its step`() {
        assertEquals(110.0, tvSeekTarget(100.0, 10.0, 600.0))
        assertEquals(90.0, tvSeekTarget(100.0, -10.0, 600.0))
    }

    @Test
    fun `back never goes before the start`() {
        assertEquals(0.0, tvSeekTarget(5.0, -10.0, 600.0))
        assertEquals(0.0, tvSeekTarget(0.0, -60.0, 600.0))
    }

    /**
     * Never onto the last second: seeking to the very end ends the stream, and
     * autoplay would take that as the video having been watched through.
     */
    @Test
    fun `forward stops one second short of the end`() {
        assertEquals(599.0, tvSeekTarget(595.0, 10.0, 600.0))
        assertEquals(599.0, tvSeekTarget(599.0, 60.0, 600.0))
    }

    /** Zero is "not known yet", which is no ceiling rather than a ceiling of zero. */
    @Test
    fun `an unknown duration puts no ceiling on the target`() {
        assertEquals(60.0, tvSeekTarget(50.0, 10.0, 0.0))
        assertEquals(10_060.0, tvSeekTarget(10_000.0, 60.0, 0.0))
    }

    @Test
    fun `an unknown duration still keeps the target off the far side of zero`() {
        assertEquals(0.0, tvSeekTarget(5.0, -10.0, 0.0))
    }

    @Test
    fun `a video shorter than a second pins the target to its start`() {
        assertEquals(0.0, tvSeekTarget(0.0, 10.0, 0.5))
    }

    // --- a run of presses -----------------------------------------------------

    @Test
    fun `a new run starts from the playhead`() {
        val draft = SeekDraft.None.press(direction = +1, positionSeconds = 100.0, durationSeconds = 600.0)

        assertEquals(SeekDraft(active = true, targetSeconds = 110.0, presses = 1), draft)
    }

    /**
     * The playhead has not moved yet — the draft is sent only when the remote
     * goes quiet — so measuring each press from it would leave three presses
     * ten seconds ahead rather than thirty.
     */
    @Test
    fun `a running draft adds up from its own target, not from the playhead`() {
        val draft = SeekDraft.None
            .press(+1, positionSeconds = 100.0, durationSeconds = 600.0)
            .press(+1, positionSeconds = 100.0, durationSeconds = 600.0)
            .press(+1, positionSeconds = 100.0, durationSeconds = 600.0)

        assertEquals(130.0, draft.targetSeconds)
        assertEquals(3, draft.presses)
    }

    /**
     * A draft that has been sent is inactive, and whatever it still holds is
     * history. The next run counts from one and starts where the picture is.
     */
    @Test
    fun `a draft that has been sent starts the next run afresh`() {
        val sent = SeekDraft(active = false, targetSeconds = 500.0, presses = 30)

        val draft = sent.press(+1, positionSeconds = 100.0, durationSeconds = 1_000.0)

        assertEquals(SeekDraft(active = true, targetSeconds = 110.0, presses = 1), draft)
    }

    @Test
    fun `back and forth in one run cancel out`() {
        val draft = SeekDraft.None
            .press(+1, positionSeconds = 100.0, durationSeconds = 600.0)
            .press(-1, positionSeconds = 100.0, durationSeconds = 600.0)

        assertEquals(100.0, draft.targetSeconds)
        assertEquals(2, draft.presses)
    }

    /** Six presses at ten, then the seventh at thirty: the count is the run's, not the direction's. */
    @Test
    fun `holding the key speeds the run up`() {
        var draft = SeekDraft.None
        repeat(6) { draft = draft.press(+1, positionSeconds = 0.0, durationSeconds = 10_000.0) }
        assertEquals(60.0, draft.targetSeconds)

        draft = draft.press(+1, positionSeconds = 0.0, durationSeconds = 10_000.0)
        assertEquals(90.0, draft.targetSeconds)
        assertEquals(7, draft.presses)
    }

    @Test
    fun `a run held to the end of a video waits one second short of it`() {
        var draft = SeekDraft.None
        repeat(40) { draft = draft.press(+1, positionSeconds = 0.0, durationSeconds = 300.0) }

        assertEquals(299.0, draft.targetSeconds)
        assertEquals(40, draft.presses)
    }

    @Test
    fun `a run held back from near the start waits at zero`() {
        val draft = SeekDraft.None
            .press(-1, positionSeconds = 4.0, durationSeconds = 300.0)
            .press(-1, positionSeconds = 4.0, durationSeconds = 300.0)

        assertEquals(0.0, draft.targetSeconds)
    }
}
