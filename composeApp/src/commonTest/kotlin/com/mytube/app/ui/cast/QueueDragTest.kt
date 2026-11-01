package com.mytube.app.ui.cast

import kotlin.test.Test
import kotlin.test.assertEquals

/** Dragging a song in the speaker's queue by its handle. */
class QueueDragTest {

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

    @Test
    fun theRowsPassedOverStepAsideTowardsWhereTheDraggedOneCameFrom() {
        // 1 dragged down to 3: rows 2 and 3 move up one row, the rest stay.
        assertEquals(listOf(0f, -100f, -100f, 0f), listOf(0, 2, 3, 4).map { queueRowShift(it, from = 1, to = 3, rowPx = 100f) })
        // 3 dragged up to 1: rows 1 and 2 move down one row.
        assertEquals(listOf(0f, 100f, 100f, 0f), listOf(0, 1, 2, 4).map { queueRowShift(it, from = 3, to = 1, rowPx = 100f) })
    }
}
