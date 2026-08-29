package com.mytube.app.ui.watch

/**
 * How far the watch screen has to be dragged before letting go closes it.
 *
 * A fraction of the screen rather than a number of pixels: a thumb's idea of
 * "most of the way down" scales with the phone, and 240px is a decisive gesture
 * on a small screen and a nudge on a tablet.
 *
 * A third, because the gesture is one-way and cheap to undo. Setting it high
 * makes closing feel like work; setting it low means a scroll that starts a few
 * pixels off vertical throws the video away.
 */
const val DISMISS_FRACTION = 0.33f

/**
 * How much of the finger's travel the screen follows.
 *
 * One to one, and deliberately not the web app's easing curve. That curve exists
 * for a pull-to-refresh, where the page must feel like it is resisting because
 * there is nothing behind it; here there genuinely is something behind — the tab
 * the viewer came from — and the gesture's whole job is to reveal it. A screen
 * that lags the finger while uncovering something reads as stiff.
 */
fun dragOffset(totalDrag: Float): Float = totalDrag.coerceAtLeast(0f)

/**
 * Whether letting go here should close the screen.
 *
 * Distance alone, with no velocity term. A flick is the same intent as a slow
 * drag past the line, and reading velocity means a quick, short flick that was
 * meant as a scroll can dismiss — which is the failure people describe as an app
 * closing on its own.
 */
fun shouldDismiss(offset: Float, screenHeight: Float): Boolean =
    screenHeight > 0 && offset > screenHeight * DISMISS_FRACTION
