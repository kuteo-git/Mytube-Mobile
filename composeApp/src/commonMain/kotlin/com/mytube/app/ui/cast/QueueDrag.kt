package com.mytube.app.ui.cast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Where a row of the speaker's queue lands when let go [offsetPx] from where it
 * started: a row further for every row's height dragged, rounded, so more than
 * half a row is the next place. Rows are all one height (one line of title, one
 * of channel), so a height is enough and no list state is needed.
 */
fun queueDropIndex(from: Int, offsetPx: Float, rowPx: Float, size: Int): Int {
    if (rowPx <= 0f || size <= 0) return from
    return (from + (offsetPx / rowPx).roundToInt()).coerceIn(0, size - 1)
}

/**
 * How far row [index] steps aside while the row at [from] is held over [to]:
 * the rows it has passed move one row towards where it came from, which is the
 * gap it will leave.
 */
fun queueRowShift(index: Int, from: Int, to: Int, rowPx: Float): Float = when {
    from < 0 || index == from -> 0f
    index in (from + 1)..to -> -rowPx
    index in to until from -> rowPx
    else -> 0f
}

/** The row being dragged by its handle, if any; held above the list so every row reads it. */
class QueueDrag {
    var from by mutableIntStateOf(-1)
    var offsetPx by mutableFloatStateOf(0f)
    var rowPx by mutableFloatStateOf(0f)

    fun to(size: Int) = queueDropIndex(from, offsetPx, rowPx, size)
}
