package com.mytube.app.ui.shell

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester

/**
 * Which control focus was last on, so it can be handed back.
 *
 * # Why it exists
 *
 * On a television the player covers whatever page it was opened from, and a
 * page opened from a tab covers the tab — and what is covered stays composed
 * underneath. Uncovering it has to put focus back where it was, or the page
 * takes it from its first control and scrolls up to meet it: reported on every
 * page as the list "resetting" when a video was put away, and on Settings as
 * back from a row landing on the first row.
 *
 * `focusRestorer` was tried first, on Home, and did not do it: asked for focus
 * as the player closed, the column gave it to its first card. Compose offers no
 * way to ask what is focused, so the controls say so themselves: every control
 * with a press lifts through `pressLift`, which writes here as it gains focus.
 * That is one line in one place rather than one per page — it was first put in
 * `pressable` alone, and the Settings rows, which hold a press of their own,
 * were never written down.
 *
 * Provided only on a television. A phone has no focus to give back, and with
 * nothing provided the controls attach nothing.
 */
class FocusMemory {
    /** The controls focus was last on, newest first, a few deep. */
    private val recent = ArrayDeque<FocusRequester>()

    internal fun focused(requester: FocusRequester) {
        recent.remove(requester)
        recent.addFirst(requester)
        while (recent.size > RECENT) recent.removeLast()
    }

    /**
     * Where focus is now, before something covers it, for the one who covers it
     * to hand back.
     *
     * Held by the caller rather than pushed on a stack here. Covering nests — a
     * page over a tab, the player over the page — but the uncovering does not
     * always come back in order: opening a channel from the player closes the
     * player and changes the route in one frame, and with one shared stack
     * whichever effect ran first took the other's mark.
     */
    fun mark(): FocusTrail = FocusTrail(recent.toList())
}

/**
 * The controls focus was on when [FocusMemory.mark] was asked, newest first.
 */
class FocusTrail internal constructor(private val requesters: List<FocusRequester>) {
    /**
     * Put focus back on the newest of these still there.
     *
     * Gone is ordinary: a card removed from the saved shelf through its own
     * menu is the control the menu closes onto. The control focused before it
     * is almost always its neighbour, which is where the remote was coming
     * from. False when none of them is left — scrolled out of a lazy list and
     * disposed, or dropped by a refresh — and the page's own first-focus is
     * then the honest fallback.
     */
    fun restore(): Boolean = requesters.any { it.tryFocus() }

    companion object {
        /** Nothing marked: restores nothing. */
        val None = FocusTrail(emptyList())
    }
}

/** How far back [FocusTrail.restore] looks for a control still there. */
private const val RECENT = 6

/** Focus to this control if it can take it now; false rather than a throw if it cannot. */
private fun FocusRequester.tryFocus(): Boolean =
    runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

/**
 * Take focus off whatever has it, then ask this control every frame until it
 * takes it, for up to ten seconds.
 *
 * For something arriving whose controls come with its data — a page, a sheet:
 * asked once, two frames in, the playlist page showed only its skeleton,
 * nothing could take focus, and it stayed on the hidden tab underneath, so the
 * remote moved through a page nobody could see. Focus is cleared first so
 * nothing covered keeps it while the wait goes on.
 */
suspend fun FocusRequester.claimFocus(focusManager: FocusManager): Boolean = claimFocus(focusManager, this)

/** The same, taking the first of [candidates] that can take focus on each frame. */
suspend fun claimFocus(focusManager: FocusManager, vararg candidates: FocusRequester): Boolean {
    focusManager.clearFocus(force = true)
    repeat(CLAIM_FRAMES) {
        if (candidates.any { it.tryFocus() }) return true
        withFrameNanos {}
    }
    return false
}

/**
 * The control an arriving page would rather be handed first, when the first in
 * its tree is not it. Null except on the page arriving, on a television.
 *
 * Search on the television: the first focusable thing it draws is the
 * microphone beside the query, and a search starts at the keyboard. A page
 * attaches this to that control and the app asks it before the page itself.
 */
val LocalPageEntry = staticCompositionLocalOf<FocusRequester?> { null }

/** Ten seconds of frames: longer than a page's or a sheet's controls take to arrive on the same wifi. */
private const val CLAIM_FRAMES = 600

/** The app's [FocusMemory] on a television, and null everywhere else. */
val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/**
 * Wait until what was just composed has been placed — two frames: one to
 * compose it, one to lay it out.
 *
 * Asking for focus before then asks a node that is not in the tree yet, and the
 * request quietly goes nowhere. Every "move focus once this appears" waits here,
 * so the frame count is decided once.
 */
suspend fun awaitPlaced() {
    withFrameNanos {}
    withFrameNanos {}
}

/**
 * A sheet, an alert or a menu, held as a modal on a television: focus goes in
 * as it opens, cannot leave while it is open, and goes back where it was when it
 * closes. Nothing at all on a phone, where there is no [FocusMemory].
 *
 * They are drawn from the app's root, over whatever opened them, and nothing
 * moved focus into them: "save to playlist" opened its sheet with focus still on
 * the player's Save button behind it, and nothing in the sheet could be chosen.
 * Their rows arrive with their data, so the sheet is asked every frame until one
 * takes focus — [claimFocus], the page's rule, for the page's reason.
 */
@Composable
fun Modifier.tvModal(visible: Boolean): Modifier {
    val memory = LocalFocusMemory.current ?: return this
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Composed shut, all of these: only a close that follows an open restores.
    var mark by remember { mutableStateOf(FocusTrail.None) }
    LaunchedEffect(visible) {
        if (visible) {
            mark = memory.mark()
            awaitPlaced()
            requester.claimFocus(focusManager)
        } else {
            awaitPlaced()
            mark.restore()
            mark = FocusTrail.None
        }
    }
    return this
        .focusRequester(requester)
        .focusProperties { onExit = { if (visible) cancelFocusChange() } }
        .focusGroup()
}
