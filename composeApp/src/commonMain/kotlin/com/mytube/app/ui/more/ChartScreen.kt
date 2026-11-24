package com.mytube.app.ui.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Charts, the one new subject the More page points at — not built yet.
 *
 * A placeholder rather than a dead row: the household asked for a Charts page,
 * so there is a page, and it says plainly that the thing itself is on the way.
 * It replaces the row's lie-by-omission with a true "soon".
 */
@Composable
fun ChartScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current

    Box(Modifier.fillMaxSize().glassSource()) {
        Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
            EmptyState(strings.chartTitle, strings.chartComingSoon)
        }
        DetailBack(onBack, strings.back)
    }
}

// --- previews ---------------------------------------------------------------

@Preview
@Composable
private fun ChartPreview() = MytubeTheme {
    ChartScreen(onBack = {})
}

@Preview
@Composable
private fun ChartVietnamesePreview() = MytubeTheme {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        ChartScreen(onBack = {})
    }
}
