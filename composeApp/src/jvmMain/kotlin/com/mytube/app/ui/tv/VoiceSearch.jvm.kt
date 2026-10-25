package com.mytube.app.ui.tv

import androidx.compose.runtime.Composable

/** None. This target exists to run the tests and the screenshot render. */
@Composable
actual fun rememberVoiceSearch(languageCode: String, onResult: (String) -> Unit): (() -> Unit)? = null
