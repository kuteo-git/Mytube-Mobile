package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/**
 * Fill the screen with the picture, and give it back.
 *
 * `expect/actual` rather than a port, and this is the case the rule was written
 * for: there is no object with methods here, only a call into whatever the
 * platform's window is. Android turns the device sideways and hides the system
 * bars; iOS is a no-op, because there is no Xcode project in this repository and
 * writing an untested rotation would be a claim with nothing behind it.
 *
 * A composable rather than a function, because both platforms need the thing
 * that owns the window — an Activity, a UIViewController — and finding it is the
 * work. Called with the state rather than as an event, so a screen that is
 * disposed while fullscreen cannot leave the phone stuck sideways.
 */
@Composable
expect fun ApplyFullscreen(enabled: Boolean)
