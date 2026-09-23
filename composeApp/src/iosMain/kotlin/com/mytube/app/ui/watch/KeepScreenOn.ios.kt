package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * iOS has one switch for this and it belongs to the whole application.
 *
 * `idleTimerDisabled` is a property of `UIApplication`, not of a view or a
 * controller, so it is process-wide — which is why it is set from the state and
 * put back on dispose rather than merely turned on. The system also clears it
 * when the app leaves the foreground and restores it on return, so a video
 * backgrounded mid-playback does not pin a display nobody is looking at.
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}
