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
 * @param bottomCollapsed the tab bar narrows to the selected glyph, opening a
 *   berth the miniplayer walks into.
 */
data class BarTravel(
    val topHidden: Boolean,
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
fun barTravel(barsShowing: Boolean, playerRestsOnBar: Boolean): BarTravel = BarTravel(
    // The top bar has nothing resting on it and nothing to navigate with, so it
    // leaves the moment a reader is moving down a feed.
    topHidden = !barsShowing,
    // **The tab bar never leaves.** It narrows or it stays whole.
    //
    // It used to slide off the bottom whenever nothing was playing, and that
    // was asked for and then reversed once it could be seen: *"tao muốn khi ko
    // mini player, nó vẫn hiện dù scroll"*. The reason it reads wrong is that
    // this bar is not chrome over the content, it is how somebody leaves the
    // page — and a reader half way down a feed who wants Settings should not
    // have to flick back up to find the way there. It is also what this app's
    // own reference does: Apple Music's tab bar collapses around a playing
    // track and never disappears.
    //
    // So `bottomHidden` is gone rather than pinned to false: a value nothing
    // can ever set is a value somebody will one day wire up again.
    bottomCollapsed = !barsShowing && playerRestsOnBar,
)

/**
 * What a press on a tab means.
 *
 * Three outcomes, because the same press means different things depending on
 * what the bar is doing when it lands.
 */
enum class TabPress {
    /** Open the bar, and move nothing. */
    Reveal,

    /** The tab already selected, pressed while the bar is whole. */
    ScrollToTop,

    /** A different tab. */
    Switch,
}

/**
 * Which of the three a press is.
 *
 * # Why a collapsed bar swallows the press
 *
 * Pressing the tab you are on goes back to the top, and that is right while the
 * bar is whole: the only alternative is swiping until your thumb aches. But
 * collapsed, the bar is a single circle and **that press is the one that opens
 * it** — reaching for it is reaching for the bar, not for the top of the feed.
 * Reported exactly that way: *"khi cái menu bị collapse lại, bấm Home → nó tự
 * expand ra rồi scroll list lên top… chỉ expand ra, ko scroll lên top"*.
 *
 * # And the reveal has to be said out loud
 *
 * It used to be free, and that was an accident worth recording. Nothing in the
 * bar answered a press at all: `rememberBarsVisible` returns
 * `atTop || !hidden`, so the bar opened only because the scroll-to-top reached
 * the top. Measured by taking the scroll away and leaving everything else — the
 * list stayed put and the bar **stayed collapsed**, which is why [TabPress] has
 * a `Reveal` rather than the fix being a deleted `if`.
 */
fun tabPress(barCollapsed: Boolean, pickedIsCurrent: Boolean): TabPress = when {
    barCollapsed -> TabPress.Reveal
    pickedIsCurrent -> TabPress.ScrollToTop
    else -> TabPress.Switch
}
