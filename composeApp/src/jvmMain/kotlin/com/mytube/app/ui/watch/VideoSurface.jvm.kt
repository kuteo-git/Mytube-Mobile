package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mytube.app.domain.repository.VideoPlayer

/**
 * A black rectangle.
 *
 * The JVM target exists to run tests and render screenshots; no desktop app is
 * shipped from it and there is no JVM player behind the port. Drawing the box
 * keeps the watch screen's layout honest in a screenshot — the picture occupies
 * the space it will occupy on a phone — without pretending anything plays.
 */
@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    Box(modifier.background(Color.Black))
}
