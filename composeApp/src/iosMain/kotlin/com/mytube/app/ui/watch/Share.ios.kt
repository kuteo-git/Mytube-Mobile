package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/**
 * Not implemented, and saying so.
 *
 * `UIActivityViewController` is the equivalent and needs the presenting view
 * controller, which cannot be reached or tested without an Xcode project. The
 * button is drawn on both platforms because the row's shape is the same; on iOS
 * it does nothing yet, which is recorded here rather than left to be found.
 */
@Composable
actual fun rememberShare(): (String) -> Unit = {}
