package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/**
 * Hand a link to whatever the platform uses for sharing.
 *
 * `expect/actual` for the same reason as fullscreen: there is no object with
 * methods here, only a call into the system. Returns a function rather than
 * taking the link, because the sheet is opened from a click and the platform
 * handle has to be resolved during composition.
 *
 * The link is the YouTube address, which is what the server charter says Share
 * means in this system: *"Share → the YouTube link"*. Sharing a LAN address
 * would hand somebody a URL that only works inside this house.
 */
@Composable
expect fun rememberShare(): (String) -> Unit
