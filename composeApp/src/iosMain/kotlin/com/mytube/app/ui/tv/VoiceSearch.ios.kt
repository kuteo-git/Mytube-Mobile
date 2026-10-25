package com.mytube.app.ui.tv

import androidx.compose.runtime.Composable

/** None: the television is Android's alone, and a phone searches by keyboard. */
@Composable
actual fun rememberVoiceSearch(languageCode: String, onResult: (String) -> Unit): (() -> Unit)? = null
