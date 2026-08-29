package com.mytube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The root, and for now only enough of it to prove the build.
 *
 * Nothing about this app's real shape is decided here yet — the screens, the
 * navigation and the design tokens all come after the toolchain is known to
 * work on both platforms. Proving a Compose tree compiles for Android and iOS
 * from one source is the first thing worth knowing, and it is worth knowing
 * before any of it is written.
 */
@Composable
fun App() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Mytube")
        }
    }
}
