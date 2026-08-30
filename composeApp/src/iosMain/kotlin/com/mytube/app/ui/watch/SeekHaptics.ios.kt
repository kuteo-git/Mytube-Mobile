package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * `UISelectionFeedbackGenerator`, which is the generator for this exact case: a
 * value being swept through positions. Not an impact — an impact is a collision,
 * and a seek bar has no wall to hit.
 *
 * `prepare()` on creation and again after every tick. The Taptic Engine takes a
 * few hundred milliseconds to wake, and a generator that is not warm fires late
 * enough that the buzz belongs to the notch before last.
 */
@Composable
actual fun rememberSeekTick(): () -> Unit {
    val generator = remember { UISelectionFeedbackGenerator().apply { prepare() } }
    return remember(generator) {
        {
            generator.selectionChanged()
            generator.prepare()
        }
    }
}
