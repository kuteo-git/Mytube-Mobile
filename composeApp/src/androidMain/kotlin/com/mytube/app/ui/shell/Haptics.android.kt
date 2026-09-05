package com.mytube.app.ui.shell

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
actual fun rememberSelectionTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }
}

/**
 * `CONTEXT_CLICK`, the firmest of the constants that are still a single knock.
 *
 * Not `CONFIRM`: that one is Android's answer to a task completing — a
 * fingerprint accepted, a payment taken — and a video sliding into a bar is not
 * an outcome anybody was waiting on. Not `LONG_PRESS` either, which is a
 * gesture being recognised rather than a thing arriving.
 */
@Composable
actual fun rememberLandingKnock(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    }
}
