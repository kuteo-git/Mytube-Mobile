package com.mytube.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * `UISelectionFeedbackGenerator`, the generator for this exact case: a value
 * being swept through positions. Not an impact — an impact is a collision, and
 * a seek bar has no wall to hit.
 *
 * `prepare()` on creation and again after every tick. The Taptic Engine takes a
 * few hundred milliseconds to wake, and a generator that is not warm fires late
 * enough that the buzz belongs to the notch before last.
 */
@Composable
actual fun rememberSelectionTick(): () -> Unit {
    val generator = remember { UISelectionFeedbackGenerator().apply { prepare() } }
    return remember(generator) {
        {
            generator.selectionChanged()
            generator.prepare()
        }
    }
}

/**
 * `UIImpactFeedbackGenerator`, light, which is the one that reads as a thing
 * coming to rest.
 *
 * Light rather than medium or heavy: the video is not dropped, it is placed —
 * it has been under a finger for the whole journey and arrives at walking pace.
 * A heavy knock at the end of a slow gesture reads as something going wrong.
 */
@Composable
actual fun rememberLandingKnock(): () -> Unit {
    val generator = remember {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply { prepare() }
    }
    return remember(generator) {
        {
            generator.impactOccurred()
            generator.prepare()
        }
    }
}
