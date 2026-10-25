package com.mytube.app.ui.tv

import androidx.compose.runtime.Composable

/**
 * Starts the platform's speech recogniser, or null where there is none.
 *
 * Null draws no microphone at all — a control that does nothing is the dead
 * button the charter forbids. On a television it is the remote's microphone
 * and Google's recogniser, the same one YouTube's TV app listens through; on a
 * phone the search screen has a keyboard and does not ask.
 *
 * [languageCode] is the app's language, so a Vietnamese household is heard in
 * Vietnamese whatever the television's own language is set to.
 */
@Composable
expect fun rememberVoiceSearch(languageCode: String, onResult: (String) -> Unit): (() -> Unit)?
