package com.mytube.app.ui.tv

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.focusGroup
import com.mytube.app.domain.model.Video
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import com.mytube.app.ui.shell.awaitPlaced
import kotlinx.coroutines.launch

/**
 * Which card a row of cards gives focus to as the remote comes into it from
 * above or below: the card it was left on, followed by its id if the row has
 * reordered since, or the place it was if it has gone — and the first card of
 * a row never visited.
 *
 * The geometric search Compose does by default is what this replaces. A shelf
 * that has scrolled holds its focused card a third of the way across, over the
 * next shelf's second card, and moving down landed there every time — reported
 * as the second card being "the default". YouTube's TV app keeps each shelf's
 * place.
 */
internal fun rowEntryIndex(ids: List<String>, lastId: String, lastIndex: Int): Int {
    if (ids.isEmpty()) return 0
    val followed = ids.indexOf(lastId)
    return if (followed >= 0) followed else lastIndex.coerceIn(0, ids.lastIndex)
}

/**
 * A row's scroll and the card it was left on, held above the lazy column the
 * row is an item of — a `remember` inside a lazy item is a cache, and a shelf
 * scrolled off the top and back would have forgotten both.
 */
@Stable
class RowMemory {
    val scroll = LazyListState()

    // Plain fields, not snapshot state: written on every press of left or
    // right, and read only as focus comes into the row or its list changes. As
    // state, every press recomposed every card on the shelf.
    var lastId = ""
        private set
    var lastIndex = 0
        private set

    /** The video focus is on — see [keptInPlace]. */
    var lastVideo: Video? = null

    /** Whether focus is in this row now — see [keptInPlace]. */
    var holding by mutableStateOf(false)

    /** Each composed card's requester, by video id, for [entersAt] to choose from. */
    internal val cards = mutableMapOf<String, FocusRequester>()

    fun focused(index: Int, id: String) {
        lastIndex = index
        lastId = id
    }

    /** Back to the first card: the rail's tab picked again. */
    fun forget() {
        lastIndex = 0
        lastId = ""
    }
}

/**
 * Focus the remote moves into this row goes to the card at [index], through
 * [requester] — chosen as focus arrives, so nothing has to be recomposed to
 * say which card it will be.
 *
 * Only a move. A card asked for by name — focus handed back as a sheet closes,
 * see `FocusMemory` — arrives as `Enter` and is let through: up next sent the
 * save sheet's way back to its first card, away from the one it was opened
 * from.
 *
 * That card can be out of the row's composition — the row was scrolled back to
 * its start by somebody else — and then the move is put off until the row has
 * been scrolled to it.
 */
@Composable
internal fun Modifier.entersAt(
    state: LazyListState,
    index: () -> Int,
    requester: (Int) -> FocusRequester?,
): Modifier {
    val scope = rememberCoroutineScope()
    val target by rememberUpdatedState(index)
    val card by rememberUpdatedState(requester)
    return focusProperties {
        onEnter = {
            if (requestedFocusDirection != FocusDirection.Enter) {
                val at = target()
                val entry = card(at)
                if (entry == null || !entry.requestFocus(FocusDirection.Enter)) {
                    cancelFocusChange()
                    scope.launch {
                        state.scrollToItem(at)
                        awaitPlaced()
                        card(at)?.requestFocus()
                    }
                }
            }
        }
    }.focusGroup()
}

/**
 * [items], with the card focus is on kept at [at] if the new list dropped it.
 *
 * Coming back from the player Home asks again, and Missed answers without the
 * video just watched — the card focus was handed back to. A focused node that
 * leaves the composition takes focus with it, and Compose gave it to the first
 * thing on screen: the rail, which opened over the shelves. Kept until focus
 * moves off it, the card goes then, quietly, from a row nobody is looking at.
 */
internal fun <T> keptInPlace(items: List<T>, focused: T?, at: Int, id: (T) -> String): List<T> {
    if (focused == null) return items
    val focusedId = id(focused)
    if (items.any { id(it) == focusedId }) return items
    return items.toMutableList().apply { add(at.coerceIn(0, size), focused) }
}
