package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/**
 * Not implemented, and saying so.
 *
 * iOS rotation is a property of the view controller and of the Info.plist's
 * supported orientations, which this app pins to portrait to match Android. Both
 * halves are straightforward and **neither can be run here**: there is no Xcode
 * project in this repository. Writing it untested would put a claim behind an
 * unmeasured change, and the picture already fills the width in portrait.
 */
@Composable
actual fun ApplyFullscreen(enabled: Boolean) = Unit
