package com.mytube.app.ui

import com.mytube.app.ui.shell.Tab

/**
 * Somewhere back can return to: the page, the tab under it, and the video
 * expanded over it when the next page was opened ("" when none was).
 */
internal data class Place(val route: Route, val tab: Tab, val watched: String)

/**
 * Where somebody has been, most recent last. Back returns to exactly the last
 * place, whichever page opened the current one.
 *
 * It replaced one remembered origin for channels and a fixed answer for every
 * other page (Home, or the Playlists tab), which sent a page opened from the
 * watch screen or from search to somewhere the viewer had not been.
 */
internal data class BackStack(val places: List<Place> = emptyList()) {
    fun push(from: Place) = BackStack(places + from)

    /** The place to return to and what is left, or null when nothing is. */
    fun pop(): Pair<Place, BackStack>? = places.lastOrNull()?.let { it to BackStack(places.dropLast(1)) }
}

/** What the edge swipe reports: how far across it is, and how it ended. */
internal sealed interface EdgeSwipe {
    data class Move(val fraction: Float) : EdgeSwipe
    data class Release(val commit: Boolean) : EdgeSwipe
}
