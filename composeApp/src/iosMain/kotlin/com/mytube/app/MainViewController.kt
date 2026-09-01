package com.mytube.app

import androidx.compose.ui.window.ComposeUIViewController
import com.mytube.app.data.local.IosSettingsDataSource
import com.mytube.app.player.AvVideoPlayerFactory
import com.mytube.app.ui.App
import platform.UIKit.UIViewController

/**
 * The iOS entry point, called from `ContentView.swift`.
 *
 * The counterpart of `MainActivity`, and the same shape: build the container
 * once for the process, then hand it to `App`. Everything above this line is
 * Swift because iOS needs an entry point; nothing below it is.
 *
 * The container is a process-wide singleton for the reason the Android side
 * gives: it owns an `HttpClient`, which owns a connection pool, and rebuilding
 * it means a fresh TCP handshake to the same server on the same wifi.
 *
 * `nativeGlass` is true only on iOS 26, where SwiftUI draws the player's panes
 * system's Liquid Glass. It is a parameter rather than a check inside Kotlin
 * because the availability test belongs where the SwiftUI view is written —
 * `#available` has no Kotlin equivalent that means the same thing.
 */
fun MainViewController(nativeGlass: Boolean): UIViewController =
    ComposeUIViewController {
        App(container, nativeGlass = nativeGlass)
    }

private val container: AppContainer by lazy {
    AppContainer(
        settings = IosSettingsDataSource(),
        playerFactory = AvVideoPlayerFactory(),
    )
}
