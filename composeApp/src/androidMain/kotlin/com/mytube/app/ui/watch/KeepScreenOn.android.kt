package com.mytube.app.ui.watch

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * The window flag, rather than a wake lock of this app's own.
 *
 * `FLAG_KEEP_SCREEN_ON` is held by the **window**, so the system releases it the
 * moment this app stops being the thing on screen — backgrounded, covered by a
 * call, or simply left. A `SCREEN_BRIGHT_WAKE_LOCK` would need `WAKE_LOCK`
 * permission and would have to be released by hand from every path out of the
 * screen, and the one that gets forgotten is the one that leaves a phone lit in
 * somebody's pocket. The platform's own guidance says the same.
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return

    DisposableEffect(enabled) {
        val window = activity.window
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // Cleared on the way out as well as when the flag goes false: a screen
        // disposed mid-video — pressing back out of the app while it plays —
        // would otherwise leave the flag on a window nobody is looking at.
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
