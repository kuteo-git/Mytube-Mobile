package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/**
 * Hold the screen awake while a video is playing, and let it go afterwards.
 *
 * ## Why this had to be said out loud
 *
 * Nothing else says it. Both platforms treat "the screen may sleep" as the
 * default and both are right to: a phone whose display stays lit because an app
 * forgot to stop asking is a flat battery by lunchtime. What neither platform
 * can know is that the picture on screen is *moving* — no touch arrives for
 * twenty minutes of a film, so the display timeout fires over a video somebody
 * is watching. Reported exactly that way, in both modes.
 *
 * Media3 holds a `PARTIAL_WAKE_LOCK` of its own while it plays, and that is a
 * different thing: it keeps the **CPU** awake so the sound survives the screen
 * going off, which is §1 of this project's whole reason for existing. It says
 * nothing about the display. Measured on the emulator with a video playing:
 * `Wake Locks: PARTIAL_WAKE_LOCK 'ExoPlayer:WakeLockManager'`, and the app
 * window's flags carrying no `KEEP_SCREEN_ON` at all.
 *
 * ## The two decisions in it
 *
 * **Playing, not open.** A paused video is a still frame, and somebody who
 * paused to read the comments has left the phone to its own timeout like any
 * other page. This is what the reference does and it is the honest reading of
 * "keep the screen on for *this*".
 *
 * **The expanded player, not the miniplayer.** The bar plays on across tabs and
 * with the screen off — that is the feature, not an oversight — so holding the
 * display awake for it would be this app spending battery on the one case it
 * was built to make cheap.
 *
 * `expect/actual` rather than a port, for [ApplyFullscreen]'s reason: there is
 * no object with methods here, only a call into whatever owns the platform's
 * window. A composable, because finding that owner is the work. And the state
 * rather than an event, so a screen torn down mid-video cannot leave the
 * display pinned on — the lesson `ApplyFullscreen` records about being left
 * sideways, in a form nobody would see until the battery was gone.
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)
