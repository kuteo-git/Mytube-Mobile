package com.mytube.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.watch.ACTIONS_ROW_HEIGHT
import com.mytube.app.ui.watch.FullscreenPull
import com.mytube.app.ui.watch.PULL_COMMIT
import com.mytube.app.ui.watch.controlRowBottom
import com.mytube.app.ui.watch.pullCommits
import com.mytube.app.ui.watch.pullDistance
import com.mytube.app.ui.watch.pullProgress
import com.mytube.app.ui.watch.returnCommits
import com.mytube.app.ui.watch.seekLineFromBottom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullProgressTest {

    @Test
    fun `at rest nothing has been pulled`() {
        assertEquals(0f, pullProgress(0f, 400f))
    }

    @Test
    fun `halfway is one half`() {
        assertEquals(0.5f, pullProgress(200f, 400f))
    }

    @Test
    fun `past the distance it stays at one`() {
        assertEquals(1f, pullProgress(900f, 400f))
    }

    @Test
    fun `a negative offset is zero`() {
        assertEquals(0f, pullProgress(-50f, 400f))
    }

    @Test
    fun `a distance of zero or less gives zero rather than a division`() {
        assertEquals(0f, pullProgress(100f, 0f))
        assertEquals(0f, pullProgress(100f, -10f))
    }
}

class PullCommitsTest {
    private val fling = 1000f

    @Test
    fun `a short slow pull springs back`() {
        assertFalse(pullCommits(PULL_COMMIT - 0.01f, 0f, fling))
    }

    @Test
    fun `a pull exactly at the threshold commits`() {
        assertTrue(pullCommits(PULL_COMMIT, 0f, fling))
    }

    @Test
    fun `a long slow pull commits`() {
        assertTrue(pullCommits(1f, 0f, fling))
    }

    @Test
    fun `a fast downward fling commits from a short pull`() {
        assertTrue(pullCommits(0.05f, fling + 1f, fling))
    }

    @Test
    fun `a fling at exactly the speed is not yet a fling`() {
        assertFalse(pullCommits(0.05f, fling, fling))
    }

    @Test
    fun `a fast upward fling never commits even past the threshold`() {
        assertFalse(pullCommits(0.9f, -(fling + 1f), fling))
    }

    @Test
    fun `an upward move slower than a fling leaves the distance to decide`() {
        assertTrue(pullCommits(0.9f, -(fling - 1f), fling))
        assertFalse(pullCommits(0.1f, -(fling - 1f), fling))
    }

    @Test
    fun `zero progress never commits, however fast the fling`() {
        assertFalse(pullCommits(0f, fling * 10, fling))
    }
}

class PullDistanceTest {

    @Test
    fun `the picture ends centred on the screen`() {
        val screen = 2000f
        val picture = 600f
        val top = 100f
        assertEquals((screen - picture) / 2f, top + pullDistance(screen, picture, top))
    }

    @Test
    fun `a picture already at or past the middle still moves a pixel`() {
        assertEquals(1f, pullDistance(1000f, 600f, 200f))
        assertEquals(1f, pullDistance(1000f, 600f, 900f))
    }
}

class FullscreenPullTest {
    private val user = NestedScrollSource.UserInput
    private val fling = NestedScrollSource.SideEffect

    private class Releases {
        val commits = mutableListOf<Boolean>()
    }

    private fun pull(enabled: Boolean = true, releases: Releases = Releases()): Pair<FullscreenPull, Releases> {
        var on = enabled
        val p = FullscreenPull(enabled = { on }, flingVelocity = 1000f, onRelease = { commits -> releases.commits += commits })
        p.distance = 400f
        return p to releases
    }

    private fun FullscreenPull.down(dy: Float, source: NestedScrollSource = user) =
        connection.onPostScroll(Offset.Zero, Offset(0f, dy), source)

    private fun FullscreenPull.up(dy: Float, source: NestedScrollSource = user) =
        connection.onPreScroll(Offset(0f, -dy), source)

    @Test
    fun `leftover downward scroll pulls and is fully consumed`() {
        val (p, _) = pull()
        val used = p.down(120f)
        assertEquals(120f, p.offset)
        assertEquals(Offset(0f, 120f), used)
    }

    @Test
    fun `the pull stops at the distance and still consumes it all`() {
        val (p, _) = pull()
        p.down(300f)
        val used = p.down(300f)
        assertEquals(400f, p.offset)
        assertEquals(Offset(0f, 300f), used)
        assertEquals(1f, p.progress)
    }

    @Test
    fun `a pull cannot start when it is not enabled`() {
        val (p, _) = pull(enabled = false)
        assertEquals(Offset.Zero, p.down(100f))
        assertEquals(0f, p.offset)
    }

    @Test
    fun `a pull already started continues after it stops being enabled`() {
        var on = true
        val p = FullscreenPull(enabled = { on }, flingVelocity = 1000f, onRelease = {})
        p.distance = 400f
        p.down(50f)
        on = false
        assertEquals(Offset(0f, 30f), p.down(30f))
        assertEquals(80f, p.offset)
    }

    @Test
    fun `scroll that is not the finger's is ignored`() {
        val (p, _) = pull()
        assertEquals(Offset.Zero, p.down(100f, fling))
        assertEquals(0f, p.offset)
        p.down(100f)
        assertEquals(Offset.Zero, p.up(40f, fling))
        assertEquals(100f, p.offset)
    }

    @Test
    fun `an upward move with nothing pulled is left to the list`() {
        val (p, _) = pull()
        assertEquals(Offset.Zero, p.up(40f))
        assertEquals(0f, p.offset)
    }

    @Test
    fun `an upward move gives the pull back first`() {
        val (p, _) = pull()
        p.down(100f)
        val used = p.up(40f)
        assertEquals(60f, p.offset)
        assertEquals(Offset(0f, -40f), used)
    }

    @Test
    fun `an upward move larger than the pull takes only the pull and leaves the rest`() {
        val (p, _) = pull()
        p.down(30f)
        val used = p.up(100f)
        assertEquals(0f, p.offset)
        assertEquals(Offset(0f, -30f), used)
    }

    @Test
    fun `a downward move in the pre pass is not the pull's`() {
        val (p, _) = pull()
        p.down(100f)
        assertEquals(Offset.Zero, p.connection.onPreScroll(Offset(0f, 40f), user))
        assertEquals(100f, p.offset)
    }

    @Test
    fun `a fling with nothing pulled consumes nothing and releases nothing`() = runTest {
        val (p, r) = pull()
        assertEquals(Velocity.Zero, p.connection.onPreFling(Velocity(0f, 5000f)))
        assertTrue(r.commits.isEmpty())
    }

    @Test
    fun `a fling after a pull releases once with the decision and takes the velocity`() = runTest {
        val (p, r) = pull()
        p.down(200f)
        val v = Velocity(0f, 10f)
        assertEquals(v, p.connection.onPreFling(v))
        assertEquals(listOf(true), r.commits)
    }

    @Test
    fun `a short slow release springs back`() = runTest {
        val (p, r) = pull()
        p.down(20f)
        p.connection.onPreFling(Velocity.Zero)
        assertEquals(listOf(false), r.commits)
    }

    @Test
    fun `a fast downward fling from a short pull commits`() = runTest {
        val (p, r) = pull()
        p.down(20f)
        p.connection.onPreFling(Velocity(0f, 5000f))
        assertEquals(listOf(true), r.commits)
    }

    @Test
    fun `a fast upward fling from a long pull does not commit`() = runTest {
        val (p, r) = pull()
        p.down(350f)
        p.connection.onPreFling(Velocity(0f, -5000f))
        assertEquals(listOf(false), r.commits)
    }

    @Test
    fun `atCentre puts the page at the distance and reset puts it back`() {
        val (p, _) = pull()
        p.atCentre()
        assertEquals(400f, p.offset)
        assertEquals(1f, p.progress)
        p.reset()
        assertEquals(0f, p.offset)
        assertEquals(0f, p.progress)
    }
}

class ActionsRowClearanceTest {
    // The arithmetic PlayerControls uses for `bottomClear` when an actions row is drawn.
    private fun bottomClear(inset: androidx.compose.ui.unit.Dp, actions: Boolean) =
        inset + if (actions) ACTIONS_ROW_HEIGHT + Space.sm else 0.dp

    @Test
    fun `the seek line rises by the actions row and one gap`() {
        for (inset in listOf(0.dp, 34.dp)) {
            assertEquals(
                seekLineFromBottom(inset) + ACTIONS_ROW_HEIGHT + Space.sm,
                seekLineFromBottom(bottomClear(inset, actions = true)),
            )
        }
    }

    @Test
    fun `without an actions row nothing moves`() {
        assertEquals(seekLineFromBottom(34.dp), seekLineFromBottom(bottomClear(34.dp, actions = false)))
    }

    @Test
    fun `the control row rises with it in fullscreen and keeps its margin outside`() {
        val clear = bottomClear(34.dp, actions = true)
        assertEquals(controlRowBottom(true, 34.dp) + ACTIONS_ROW_HEIGHT + Space.sm, controlRowBottom(true, clear))
        assertEquals(Space.sm, controlRowBottom(false, clear))
    }
}

/**
 * Upright fullscreen dragged back up, slowly, the way YouTube's is: the page
 * follows the finger up from the middle and, let go far enough, returns.
 * Reported from the phone as "pulled down, then pulling up does nothing".
 */
class FullscreenReturnTest {
    private fun centred(): FullscreenPull {
        val p = FullscreenPull(enabled = { true }, flingVelocity = 1000f, onRelease = {})
        p.distance = 400f
        p.atCentre()
        return p
    }

    @Test
    fun `a finger moving up carries the page up with it`() {
        val p = centred()
        p.dragBy(-100f)
        assertEquals(300f, p.offset)
    }

    @Test
    fun `the page never goes above its place or below the middle`() {
        val p = centred()
        p.dragBy(-900f)
        assertEquals(0f, p.offset)
        p.dragBy(900f)
        assertEquals(400f, p.offset)
    }

    @Test
    fun `dragged a third of the way up and let go slowly, the page returns`() {
        assertTrue(returnCommits(progress = 1f - PULL_COMMIT, velocity = 0f, flingVelocity = 1000f))
    }

    @Test
    fun `a nudge up and let go slowly goes back to fullscreen`() {
        assertFalse(returnCommits(progress = 0.9f, velocity = 0f, flingVelocity = 1000f))
    }

    @Test
    fun `a fast flick up returns from a short drag`() {
        assertTrue(returnCommits(progress = 0.95f, velocity = -1500f, flingVelocity = 1000f))
    }

    @Test
    fun `a fast flick back down stays in fullscreen however far up it was`() {
        assertFalse(returnCommits(progress = 0.2f, velocity = 1500f, flingVelocity = 1000f))
    }

    @Test
    fun `not moved at all stays in fullscreen`() {
        assertFalse(returnCommits(progress = 1f, velocity = -1500f, flingVelocity = 1000f))
    }
}
