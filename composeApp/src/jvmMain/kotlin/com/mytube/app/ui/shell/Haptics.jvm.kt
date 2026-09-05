package com.mytube.app.ui.shell

import androidx.compose.runtime.Composable

/** A desktop has nothing to buzz. The JVM target exists to run tests. */
@Composable
actual fun rememberSelectionTick(): () -> Unit = {}

/** @see rememberSelectionTick */
@Composable
actual fun rememberLandingKnock(): () -> Unit = {}
