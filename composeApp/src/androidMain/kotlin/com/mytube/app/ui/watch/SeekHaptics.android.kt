package com.mytube.app.ui.watch

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * `CLOCK_TICK`, which is Android's name for the same idea — the feedback a
 * picker gives as a value passes each position. Short and dry, against
 * `KEYBOARD_TAP`, which is louder and belongs to a press.
 *
 * It obeys the system's haptic setting on its own, so there is nothing here to
 * check: a phone with feedback switched off gets silence from this call.
 */
@Composable
actual fun rememberSeekTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }
}
