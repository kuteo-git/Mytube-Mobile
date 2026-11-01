package com.mytube.app.ui.cast

import com.mytube.app.domain.model.CastItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Dragging a song in the speaker's queue by its handle. */
class QueueDragTest {

    private val queue = listOf("a", "b", "c", "d", "e").map { CastItem(it, it, "", "", 100) }
    private fun QueueDrag.ids() = order?.map { it.videoId }

    @Test
    fun aRowDraggedMoreThanHalfARowLandsInTheNextPlace() {
        assertEquals(2, queueDropIndex(from = 1, offsetPx = 60f, rowPx = 100f, size = 5))
        assertEquals(1, queueDropIndex(from = 1, offsetPx = 40f, rowPx = 100f, size = 5))
        assertEquals(0, queueDropIndex(from = 2, offsetPx = -170f, rowPx = 100f, size = 5))
    }

    @Test
    fun aRowCannotBeDraggedOffEitherEnd() {
        assertEquals(4, queueDropIndex(from = 1, offsetPx = 900f, rowPx = 100f, size = 5))
        assertEquals(0, queueDropIndex(from = 1, offsetPx = -900f, rowPx = 100f, size = 5))
    }

    @Test
    fun beforeTheRowIsMeasuredNothingMoves() {
        assertEquals(1, queueDropIndex(from = 1, offsetPx = 300f, rowPx = 0f, size = 5))
    }

    /**
     * The held row changes place as it goes, so it is always the row under the
     * finger — which is what lets the list scroll under it without the row
     * leaving the screen, and the list with it.
     */
    @Test
    fun theHeldRowTakesItsNewPlaceAsItIsDragged() {
        val drag = QueueDrag().apply { rowPx = 100f }
        drag.start(1, queue)
        drag.dragBy(160f)
        assertEquals(listOf("a", "c", "d", "b", "e"), drag.ids())
        assertEquals(3, drag.heldIndex)
        assertEquals(-40f, drag.offsetPx, "what is left over is the distance from its new place")
        assertEquals(1 to 3, drag.end())
        assertNull(drag.order)
    }

    @Test
    fun aRowLetGoWhereItStartedMovesNothing() {
        val drag = QueueDrag().apply { rowPx = 100f }
        drag.start(2, queue)
        drag.dragBy(140f)
        drag.dragBy(-150f)
        assertEquals(listOf("a", "b", "c", "d", "e"), drag.ids())
        assertNull(drag.end())
    }

    @Test
    fun theListScrollsOnlyNearAnEdgeAndFasterTheCloser() {
        fun step(top: Float) = edgeScrollStep(top, top + 50f, viewportStart = 0f, viewportEnd = 1_000f, edgePx = 100f, maxStepPx = 20f)
        assertEquals(0f, step(400f))
        assertEquals(-10f, step(50f), "half way into the top edge, half speed upwards")
        assertEquals(-20f, step(-30f), "past it, full speed")
        assertEquals(10f, step(900f), "the row's bottom half way into the bottom edge")
        assertEquals(20f, step(990f))
    }

    @Test
    fun theSameSongTwiceHasTwoKeys() {
        val twice = listOf("a", "b", "a").map { CastItem(it, it, "", "", 100) }
        assertEquals(listOf("a#0", "b#0", "a#1"), queueKeys(twice))
    }

    /** Each new place is one tick under the finger, as a picker's notches are. */
    @Test
    fun aDragSaysWhenTheRowHasTakenANewPlace() {
        val drag = QueueDrag().apply { rowPx = 100f }
        drag.start(0, queue)
        assertEquals(false, drag.dragBy(30f))
        assertEquals(true, drag.dragBy(30f))
        assertEquals(false, drag.dragBy(10f))
    }
}
