package com.mytube.app.ui.shell

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The same vectors Android uses.
 *
 * This target exists to run tests and to render `@Preview`, so what it must show
 * is the Android face of a screen — which is what it shows.
 */
@Composable
actual fun TabGlyph(tab: Tab, selected: Boolean, tint: Color, modifier: Modifier) {
    Icon(imageVector = tabIcon(tab), contentDescription = null, tint = tint, modifier = modifier)
}
