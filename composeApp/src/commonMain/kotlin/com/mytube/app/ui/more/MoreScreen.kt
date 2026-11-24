package com.mytube.app.ui.more

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.stroke
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.pressable
import com.mytube.app.ui.shell.tabContentPadding
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The More page, the last page of the home pager.
 *
 * A page you swipe to, the way YouTube's own More tab is — not a place you leave
 * Home for. So it is an ordinary pager page under the chip row and the bar, with
 * no back arrow of its own; the chip row's selected "More" is what says where
 * you are. It is a short menu, each row an outlined icon and a label like the
 * More tab on YouTube: News (off the chip row), Live (off it too) and Charts
 * (not built yet) — each opening its own full page.
 */
@Composable
fun MoreContent(
    onOpenNews: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenCharts: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val strings = LocalStrings.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The same padding the feed pages use, so the first row sits below
            // the chip row rather than under it.
            contentPadding = tabContentPadding(),
        ) {
            item(key = "news") { MoreRow(NewsIcon, strings.newsTitle, onOpenNews) }
            item(key = "live") { MoreRow(LiveIcon, strings.liveTitle, onOpenLive) }
            item(key = "charts") { MoreRow(ChartsIcon, strings.chartTitle, onOpenCharts) }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon 24dp with a 12dp gap to the label, the way YouTube's own More
        // rows sit — tighter than the row's own side padding, so the glyph and
        // its word read as one item.
        Icon(icon, contentDescription = null, tint = Tokens.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.md))
        Text(label, color = Tokens.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// --- icons, outlined like the tab set (see TabIcons) ------------------------

/** A newspaper: a framed page with a headline block and lines. */
private val NewsIcon: ImageVector by lazy {
    stroke("News") {
        moveTo(4f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(4f, 19f); close()
        moveTo(7f, 8.5f); lineTo(11f, 8.5f); lineTo(11f, 12f); lineTo(7f, 12f); close()
        moveTo(13f, 8.5f); lineTo(15.5f, 8.5f)
        moveTo(13f, 10.5f); lineTo(15.5f, 10.5f)
        moveTo(13f, 12f); lineTo(15.5f, 12f)
        moveTo(7f, 15f); lineTo(15.5f, 15f)
        moveTo(7f, 17f); lineTo(15.5f, 17f)
    }
}

/** A broadcast: a centre dot and two pairs of signal arcs. */
private val LiveIcon: ImageVector by lazy {
    stroke("Live") {
        moveTo(10.5f, 12f)
        curveTo(10.5f, 11.2f, 11.2f, 10.5f, 12f, 10.5f)
        curveTo(12.8f, 10.5f, 13.5f, 11.2f, 13.5f, 12f)
        curveTo(13.5f, 12.8f, 12.8f, 13.5f, 12f, 13.5f)
        curveTo(11.2f, 13.5f, 10.5f, 12.8f, 10.5f, 12f)
        close()
        moveTo(8.5f, 8.5f); curveTo(6.5f, 10.5f, 6.5f, 13.5f, 8.5f, 15.5f)
        moveTo(15.5f, 8.5f); curveTo(17.5f, 10.5f, 17.5f, 13.5f, 15.5f, 15.5f)
        moveTo(6f, 6f); curveTo(2.7f, 9.3f, 2.7f, 14.7f, 6f, 18f)
        moveTo(18f, 6f); curveTo(21.3f, 9.3f, 21.3f, 14.7f, 18f, 18f)
    }
}

/** A trending-up line with an arrowhead, as YouTube draws Charts. */
private val ChartsIcon: ImageVector by lazy {
    stroke("Charts") {
        moveTo(4f, 15f); lineTo(9f, 10f); lineTo(13f, 14f); lineTo(20f, 7f)
        moveTo(15f, 7f); lineTo(20f, 7f); lineTo(20f, 12f)
    }
}

// --- previews ---------------------------------------------------------------

@Preview
@Composable
private fun MorePreview() = MytubeTheme {
    MoreContent(onOpenNews = {}, onOpenLive = {}, onOpenCharts = {})
}

@Preview
@Composable
private fun MoreVietnamesePreview() = MytubeTheme {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        MoreContent(onOpenNews = {}, onOpenLive = {}, onOpenCharts = {})
    }
}
