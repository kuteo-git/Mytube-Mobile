package com.mytube.app.ui.watch

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun ApplyFullscreen(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return

    DisposableEffect(enabled) {
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (enabled) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // A swipe brings the bars back for a moment and they leave again.
            // The alternative hides them until the next touch anywhere, which
            // means the first tap on the controls only restores the status bar.
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onDispose {
            // Leaving the screen while fullscreen must not leave the phone
            // sideways with no bars. This is the reason the seam takes a state
            // rather than an event: an event has nothing to undo.
            if (enabled) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/**
 * The Activity behind a Compose context.
 *
 * `LocalContext` is not the Activity: Compose wraps it, sometimes several times
 * over. Unwrapping is the documented way to reach the window, and returning null
 * rather than casting means a preview — which has no Activity at all — draws
 * instead of crashing.
 */
private fun android.content.Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
