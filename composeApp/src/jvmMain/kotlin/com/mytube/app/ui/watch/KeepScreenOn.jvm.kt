package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/** Nothing to hold awake: the jvm target exists to run tests. */
@Composable
actual fun KeepScreenOn(enabled: Boolean) = Unit
