package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/**
 * Nothing to do.
 *
 * The JVM target exists only to run the tests and the offscreen renderer: there
 * is no desktop app, no window of this app's own, and nothing to rotate. It is
 * here because `expect` demands an `actual` on every target, and an empty one
 * that says why is better than adding a target's worth of machinery to satisfy
 * the compiler.
 */
@Composable
actual fun ApplyFullscreen(enabled: Boolean) = Unit
