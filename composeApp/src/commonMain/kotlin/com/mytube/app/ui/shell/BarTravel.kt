package com.mytube.app.ui.shell

/**
 * How the two bars get out of a reader's way.
 *
 * Three booleans rather than one, because the two bars do not answer the same
 * question and for a while this app pretended they did. The bottom bar has the
 * miniplayer resting on it, so sliding it away takes the player with it — the
 * one thing on screen saying something is still playing. The top bar has
 * nothing resting on it at all.
 *
 * @param topHidden the top bar and everything pinned under it — today the
 *   feed's chip row.
 * @param bottomHidden the tab bar slides off the bottom edge.
 * @param bottomCollapsed the tab bar narrows to the selected glyph instead,
 *   opening a berth the miniplayer walks into. Never true with [bottomHidden].
 */
data class BarTravel(
    val topHidden: Boolean,
    val bottomHidden: Boolean,
    val bottomCollapsed: Boolean,
)

/**
 * Which bars leave, and how.
 *
 * # Why this is a named function rather than two expressions at the call site
 *
 * It was two expressions at the call site, and they shared a term that only one
 * of them was entitled to. `barsHidden` was computed once as
 * `if (barsShowing || collapsing) 0f else 1f` and handed to **both** bars — so
 * the moment anything was playing, `collapsing` pinned the top bar and the chip
 * row as well, and neither ever left again. Reported as *"Chip và bottombar
 * luôn luôn hiện, ko auto hide khi scroll nữa"*, and measured on the emulator
 * through Compose's own semantics: with the miniplayer up the chip "All" sat at
 * y=147 before three flings and y=147 after; with it closed, both nodes were
 * gone.
 *
 * Nothing in the type system catches a float being handed to one consumer too
 * many — both bars take a `Float` and both were happy — which is the same
 * reason `wholeSeconds`, `levelsFor` and `shouldRecoverStall` are named
 * functions. The answers are judgements, so they are written where a test can
 * read them.
 *
 * @param barsShowing what the scroll says: true while the reader is at the top
 *   or moving up.
 * @param playerRestsOnBar whether the miniplayer is currently sitting on the
 *   tab bar. Only then is collapsing the right answer — while the video is
 *   expanded the watch layer covers the shell, and the drag that collapses it
 *   lands where a *whole* bar is.
 */
fun barTravel(barsShowing: Boolean, playerRestsOnBar: Boolean): BarTravel {
    if (barsShowing) return BarTravel(false, false, false)
    // The top bar has nothing resting on it, so it always just leaves.
    return BarTravel(
        topHidden = true,
        bottomHidden = !playerRestsOnBar,
        bottomCollapsed = playerRestsOnBar,
    )
}
