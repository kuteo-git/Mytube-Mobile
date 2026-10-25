package com.mytube.app.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Whether this app is on a television, read by the few places that lay out
 * differently there.
 *
 * Decided once, by the entry point, from what the platform says the device *is*
 * — Android's television UI mode — and never from the window's size. A tablet
 * held sideways is wider than a television in dp and is still held by somebody
 * with fingers; what changes on a TV is that there is no finger, and nothing
 * about the width says that.
 *
 * Static, because it cannot change while the app runs, and a static local
 * costs nothing to read from every card.
 */
val LocalTelevision = staticCompositionLocalOf { false }

/**
 * How far in from the screen's sides a television's content keeps.
 *
 * Android TV's own guidance, 48dp by 27dp: a set may overscan, cropping the
 * picture's edges, and a rail or a title flush with the edge is one some sets
 * never show. The picture itself ignores this — a video is meant to be cropped
 * by an overscanning set exactly as broadcasts are.
 */
val TV_SAFE_HORIZONTAL = 48.dp
val TV_SAFE_VERTICAL = 27.dp
