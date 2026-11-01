package com.mytube.app.ui.cast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mytube.app.domain.model.CastItem
import com.mytube.app.domain.model.moved
import kotlin.math.roundToInt

/**
 * Where a row of the speaker's queue belongs when it is [offsetPx] from its
 * place: a row further for every row's height, rounded, so more than half a row
 * is the next place. Rows are all one height (one line of title, one of
 * channel), so a height is enough and no list state is needed.
 */
fun queueDropIndex(from: Int, offsetPx: Float, rowPx: Float, size: Int): Int {
    if (rowPx <= 0f || size <= 0) return from
    return (from + (offsetPx / rowPx).roundToInt()).coerceIn(0, size - 1)
}

/**
 * How far to scroll the list this frame while a row is held: nothing until the
 * row is within [edgePx] of an edge, then faster the further in, up to
 * [maxStepPx] — as every list that is reordered by dragging does.
 */
fun edgeScrollStep(
    itemTop: Float,
    itemBottom: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edgePx: Float,
    maxStepPx: Float,
): Float {
    val intoTop = viewportStart + edgePx - itemTop
    val intoBottom = itemBottom - (viewportEnd - edgePx)
    return when {
        intoTop > 0f -> -maxStepPx * (intoTop / edgePx).coerceAtMost(1f)
        intoBottom > 0f -> maxStepPx * (intoBottom / edgePx).coerceAtMost(1f)
        else -> 0f
    }
}

/**
 * Keys that stay with a song as it moves. The index cannot be in the key: the
 * held row changes index as it is dragged, and a new key is a new row — its
 * gesture gone mid-drag. The same song queued twice is told apart by which time
 * it is.
 */
fun queueKeys(queue: List<CastItem>): List<String> {
    val seen = HashMap<String, Int>()
    return queue.map { item ->
        val n = seen[item.videoId] ?: 0
        seen[item.videoId] = n + 1
        "${item.videoId}#$n"
    }
}

/**
 * A row of the speaker's queue held by its handle.
 *
 * The row takes its new place as it goes, so it is always the row under the
 * finger and the list can scroll under it without it leaving the screen —
 * which, in a lazy list, would end the row and the gesture with it. [order] is
 * the queue as it is being rearranged; [offsetPx] how far the finger is from
 * the held row's place in it. Held above the list so every row reads it.
 */
class QueueDrag {
    var order by mutableStateOf<List<CastItem>?>(null)
        private set
    var heldIndex by mutableIntStateOf(-1)
        private set
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var rowPx by mutableFloatStateOf(0f)
    private var startIndex = -1

    val holding: Boolean get() = order != null

    fun start(index: Int, queue: List<CastItem>) {
        order = queue
        startIndex = index
        heldIndex = index
        offsetPx = 0f
    }

    /** The finger, or the list under it, moved by [dy]; true when the row took a new place. */
    fun dragBy(dy: Float): Boolean {
        val current = order ?: return false
        offsetPx += dy
        val to = queueDropIndex(heldIndex, offsetPx, rowPx, current.size)
        if (to == heldIndex) return false
        order = current.moved(heldIndex, to)
        offsetPx -= (to - heldIndex) * rowPx
        heldIndex = to
        return true
    }

    /** Let go: where the row started and where it ended, or null if it is back where it was. */
    fun end(): Pair<Int, Int>? {
        val moved = (startIndex to heldIndex).takeIf { order != null && it.first != it.second }
        cancel()
        return moved
    }

    fun cancel() {
        order = null
        startIndex = -1
        heldIndex = -1
        offsetPx = 0f
    }
}
