package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable

/** A desktop has nothing to buzz. The JVM target exists to run tests. */
@Composable
actual fun rememberSeekTick(): () -> Unit = {}
