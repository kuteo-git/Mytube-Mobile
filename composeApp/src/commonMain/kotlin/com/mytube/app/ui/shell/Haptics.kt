package com.mytube.app.ui.shell

import androidx.compose.runtime.Composable

/**
 * The two things this app has to say through the phone's own feedback.
 *
 * Two rather than one, because they are two different sensations and the
 * platforms name them apart. A *selection* is a value moving through discrete
 * positions — a seek bar crossing a notch, a chip row changing which chip is
 * lit. An *impact* is something arriving and stopping — the video landing in
 * the miniplayer at the end of a drag. Mapping the second onto the first would
 * make the landing feel like one more notch in a sweep, which is the opposite
 * of what it is.
 *
 * ## Why not `LocalHapticFeedback`
 *
 * Compose has one, and it speaks in gestures — `LongPress`, `TextHandleMove` —
 * rather than in these. On iOS what is wanted is `UISelectionFeedbackGenerator`
 * and `UIImpactFeedbackGenerator`, the generators Apple made for exactly these
 * two cases; mapping either onto "text handle move" would be borrowing a name
 * for the wrong reason.
 *
 * Both return a function rather than taking the event, because both platforms
 * want to *prepare* their generator while the gesture is starting — an
 * unprepared one fires late enough to feel disconnected from the finger.
 */
@Composable
expect fun rememberSelectionTick(): () -> Unit

/**
 * One knock, for something that has arrived somewhere.
 *
 * @see rememberSelectionTick for why this is not the same call with a
 * parameter.
 */
@Composable
expect fun rememberLandingKnock(): () -> Unit
