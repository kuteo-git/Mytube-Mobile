package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations

/**
 * Fullscreen, iOS side: turn the phone sideways and put it back.
 *
 * ## Why it takes three pieces
 *
 * An iOS app cannot rotate itself on request. The system asks the root view
 * controller what it supports and rotates only within that answer, so all three
 * have to agree:
 *
 * 1. **`Info.plist`** lists portrait *and* both landscapes — the outer bound of
 *    what is ever possible. It listed portrait alone, which is why nothing here
 *    could have worked whatever it asked for.
 * 2. **The Swift app delegate** answers the system's question from
 *    [OrientationLock], narrowing that bound moment to moment.
 * 3. **This function** flips the lock, tells the controller its answer has
 *    changed, and asks the window scene to rotate.
 *
 * `setNeedsUpdateOfSupportedInterfaceOrientations` is the step that is easy to
 * miss: without it the delegate's new answer is never asked for, and the request
 * below is refused against the old one.
 *
 * ## Why the status bar is left alone
 *
 * Android hides the system bars here. On iOS the picture already extends under
 * them — `iOSApp.swift` applies `ignoresSafeArea` — and hiding them as well
 * needs `prefersStatusBarHidden` on a controller this app does not own. The
 * clock over a landscape video is a small cost against reaching into a
 * framework-built controller.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun ApplyFullscreen(enabled: Boolean) {
    DisposableEffect(enabled) {
        apply(enabled)
        onDispose {
            // Leaving the screen while fullscreen must not leave the phone
            // sideways. This is why the seam takes a state rather than an
            // event: an event has nothing to undo.
            if (enabled) apply(false)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun apply(landscape: Boolean) {
    OrientationLock.allow(landscape)

    val scene = UIApplication.sharedApplication.connectedScenes
        .firstOrNull { it is UIWindowScene } as? UIWindowScene ?: return

    // Ask again before requesting: the delegate's answer has just changed, and
    // without this the request is judged against the previous one.
    scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()

    val mask = if (landscape) {
        UIInterfaceOrientationMaskLandscape
    } else {
        UIInterfaceOrientationMaskPortrait
    }
    scene.requestGeometryUpdateWithPreferences(
        UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask),
    ) {
        // Refused, which happens when the delegate and the request disagree.
        // Swallowed rather than thrown: a video that stays portrait is a
        // disappointment, and one that takes the app down is a fault.
    }
}
