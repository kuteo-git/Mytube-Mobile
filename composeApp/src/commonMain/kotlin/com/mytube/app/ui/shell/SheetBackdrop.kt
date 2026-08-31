package com.mytube.app.ui.shell

/**
 * There is no sheet backdrop, and this file records why rather than leaving the
 * next person to find out the same way.
 *
 * # Why a sheet is not made of glass
 *
 * Both sheets set `containerColor = Tokens.bg` and that is the whole of it. Two
 * reasons, and the second is the one that decides it:
 *
 * **It cannot be done here.** `ModalBottomSheet` renders in its own popup layer
 * with its own coordinate space. Haze positions its effect from the node's
 * `positionInRoot`, which inside that layer starts at zero — so the effect drew
 * the slice of the app from the *top of the screen* into the sheet's place. On a
 * watch screen that is the video: the picture appeared blurred in its own frame
 * while the sheet's rows floated transparent over the comments underneath. That
 * is not a tuning problem; the sheet is not in the scene Haze recorded.
 *
 * **It would be wrong anyway.** iOS does not make sheets of material. A sheet is
 * `systemBackground` — opaque — with a dimmed backdrop behind it. Material is
 * for bars and toolbars, which is exactly where [BarBackdrop] uses it. So the
 * opaque sheet is not a fallback; it is the platform's answer.
 *
 * The player's sheet still passes `scrimColor = Color.Transparent`, because the
 * settings on it are about the video playing above and dimming that video is
 * dimming the thing being adjusted.
 */
private const val NOTE = "See the file comment."
