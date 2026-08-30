package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/**
 * A single tick of feedback, for the notch a scrubbing finger has just crossed.
 *
 * ## Why not `LocalHapticFeedback`
 *
 * Compose has one, and it speaks in gestures — `LongPress`, `TextHandleMove` —
 * rather than in notches. On iOS what is wanted here is
 * `UISelectionFeedbackGenerator`, the generator Apple made for exactly this: a
 * value being swept through discrete positions. It is a different sensation from
 * an impact, it is what the system's own sliders and pickers use, and mapping it
 * onto "text handle move" would be borrowing a name for the wrong reason.
 *
 * Returns a function rather than taking the event, because both platforms want
 * to *prepare* their generator while the gesture is starting — an unprepared one
 * fires late enough to feel disconnected from the finger.
 */
@Composable
expect fun rememberSeekTick(): () -> Unit
