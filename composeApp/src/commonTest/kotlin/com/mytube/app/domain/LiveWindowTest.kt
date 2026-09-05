package com.mytube.app.domain

import com.mytube.app.domain.repository.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A broadcast's timeline.
 *
 * The arithmetic is the whole of a real bug and is worth checking without a
 * device: a live stream declares no duration, so `position / duration` on a
 * broadcast 26 minutes in came to 155,700% on the web — a bar painted solid red
 * from the first second, reading "25:57 / 0:00" beside it. This app answered
 * the same measurement by drawing no bar at all, which is the fault reported
 * here: the web has one and can rewind, and this cannot.
 *
 * Ported from the web app's `live-timeline.test.ts` case for case, because a
 * second client answering the same question differently is how the two drift.
 */
class LiveWindowTest {

    private fun live(start: Double, end: Double, position: Double) = PlaybackState(
        positionSeconds = position,
        liveStartSeconds = start,
        liveEndSeconds = end,
        isLive = true,
    )

    @Test
    fun `does not paint the bar full on a stream with no declared window`() {
        val state = PlaybackState(positionSeconds = 1557.0, isLive = true)
        assertFalse(state.hasLiveWindow)
        assertEquals(0f, state.progress)
    }

    @Test
    fun `measures from the window, not from zero`() {
        // Halfway through a window running 600..1200.
        assertEquals(0.5f, live(600.0, 1200.0, 900.0).progress)
    }

    /**
     * The window slides forward, so a position can fall out of the bottom of it
     * between two reads. That is a bar at the start, not a negative one.
     */
    @Test
    fun `clamps a position the window has already left behind`() {
        assertEquals(0f, live(600.0, 1200.0, 400.0).progress)
    }

    @Test
    fun `clamps a position past the edge rather than overflowing`() {
        assertEquals(1f, live(0.0, 1285.0, 1290.0).progress)
    }

    /** Measured on the web: playback settled at 1270.1 against a window ending 1285. */
    @Test
    fun `is at the edge a couple of segments behind, because the edge keeps moving`() {
        assertTrue(live(0.0, 1285.0, 1278.0).atLiveEdge)
    }

    @Test
    fun `is not at the edge once the viewer has rewound`() {
        assertFalse(live(0.0, 1285.0, 1216.0).atLiveEdge)
    }

    @Test
    fun `is not at the edge before the window is known, so nothing claims to be live`() {
        assertFalse(PlaybackState(positionSeconds = 1557.0, isLive = true).atLiveEdge)
    }

    /**
     * The one that cost a round of measurement on the simulator.
     *
     * A seek to exactly the end of the window is ignored by AVPlayer, so the
     * bar dragged fully right and the LIVE pill both did nothing while every
     * other position worked. The target is held half a tolerance inside.
     */
    @Test
    fun `a seek to the far end lands inside the window, not on its edge`() {
        val state = live(0.0, 1285.0, 100.0)
        assertEquals(1280.0, state.seekTarget(1.0))
        assertEquals(1280.0, state.liveEdgeTarget)
        // And it is still the edge, or the pill would seek somewhere it then
        // refuses to call live.
        assertTrue(state.copy(positionSeconds = state.liveEdgeTarget).atLiveEdge)
    }

    @Test
    fun `a fraction inside the window is untouched by the clamp`() {
        assertEquals(600.0, live(0.0, 1200.0, 0.0).seekTarget(0.5))
    }

    @Test
    fun `a window that does not start at zero is measured from its start`() {
        // iOS reports the item's own timebase, which can begin anywhere.
        assertEquals(900.0, live(600.0, 1200.0, 0.0).seekTarget(0.5))
    }

    /**
     * A recorded video is untouched by any of this: the window is absent and
     * the duration decides, exactly as it did before.
     */
    @Test
    fun `a recorded video still measures against its duration`() {
        val state = PlaybackState(positionSeconds = 30.0, durationSeconds = 120.0)
        assertFalse(state.hasLiveWindow)
        assertEquals(0.25f, state.progress)
        assertEquals(60.0, state.seekTarget(0.5))
    }
    @Test
    fun `a jump on a broadcast stays inside the window`() {
        // The reported bug, and it is one line of arithmetic: a broadcast
        // declares no duration, so clamping a jump to `duration - 1` clamps it
        // to `max(-1, 0)` — zero. Every double tap, forward or back, landed an
        // hour ago at the start of the rewind window.
        val state = live(start = 0.0, end = 3600.0, position = 3000.0)

        assertEquals(3010.0, state.skipTarget(10.0))
        assertEquals(2990.0, state.skipTarget(-10.0))
    }

    @Test
    fun `jumping forward past the edge lands at the edge, not at zero`() {
        // What the viewer asked for in the same breath: skipping forward off
        // the end of a broadcast should arrive at live, which is where the bar
        // is full — the same target the LIVE pill uses, so the two agree.
        val state = live(start = 0.0, end = 3600.0, position = 3598.0)

        assertEquals(state.liveEdgeTarget, state.skipTarget(10.0))
        // Not a progress of exactly 1: `liveEdgeTarget` sits half the edge
        // tolerance inside the range on purpose, because AVPlayer ignores a
        // seek to the very end of its own seekable range. What "the bar is
        // full" means here is `atLiveEdge`, which is also what lights the LIVE
        // pill — so arriving by a double tap and arriving by the pill leave the
        // screen saying the same thing.
        assertTrue(state.copy(positionSeconds = state.skipTarget(10.0)).atLiveEdge)
    }

    @Test
    fun `jumping back past the start of the window stops at the start`() {
        val state = live(start = 600.0, end = 1200.0, position = 605.0)

        assertEquals(600.0, state.skipTarget(-10.0))
    }

    @Test
    fun `a recorded video still clamps to its own length`() {
        // Unchanged, and asserted so the live branch cannot quietly take it
        // over: the clamp short of the end is what stops a player buffering
        // toward a position that never arrives.
        val state = PlaybackState(positionSeconds = 130.0, durationSeconds = 140.0)

        assertEquals(139.0, state.skipTarget(10.0))
        assertEquals(120.0, state.skipTarget(-10.0))
        assertEquals(0.0, state.skipTarget(-500.0))
    }

}
