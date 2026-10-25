package com.mytube.app.ui.tv

import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.LocalTelevision
import com.mytube.app.ui.shell.SearchIcon
import com.mytube.app.ui.shell.TV_SAFE_VERTICAL
import com.mytube.app.ui.shell.Tab
import com.mytube.app.ui.shell.TabGlyph
import com.mytube.app.ui.shell.tabLabel
import com.mytube.app.ui.shell.awaitPlaced
import com.mytube.app.ui.shell.pressable
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The television's frame: a rail down the left, and the tab beside it.
 *
 * # Why a rail and not the phone's bar
 *
 * The phone's tab bar is at the bottom because that is where a thumb is. A
 * remote has no thumb on the screen; it has *left*, and YouTube's own TV app
 * puts its sections there for that reason — the sections are one press away
 * from the first card of any shelf, and nothing covers the shelves until
 * somebody reaches for them.
 *
 * Collapsed it is a column of glyphs; once focus is inside it, it opens over
 * the page with the labels beside them, the way that app's does.
 *
 * # Why it is painted
 *
 * Sampling the page behind it costs a blur of the whole screen on every frame
 * the rail is open, on a box whose GPU is a phone's of several years ago. The
 * charter's three materials allow a painted pane anywhere; this is a dark wash
 * over the shelves, which is what the rail *says* — "these are not the page".
 */
@Composable
fun TvShell(
    current: Tab,
    onSelect: (Tab) -> Unit,
    onSearch: () -> Unit,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (open) RAIL_OPEN else RAIL_CLOSED,
        animationSpec = tween(RAIL_MILLIS),
        label = "rail",
    )
    val strings = LocalStrings.current
    // Where focus lands on the way in: the tab somebody is on, not the first row
    // of the rail — moving left from a shelf on Settings and arriving on Search
    // would be the rail forgetting where it is.
    val requesters = remember { Tab.entries.associateWith { FocusRequester() } }
    // Picking a tab hands focus to it, as YouTube's TV app does: the rail is
    // how somebody gets somewhere, not where they stay. Counted rather than a
    // flag, so picking the tab already showing still moves focus off the rail.
    var picked by remember { mutableIntStateOf(0) }
    val returnToTop = remember { ReturnToTop() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(picked) {
        if (picked == 0) return@LaunchedEffect
        // Once the new tab is placed, or there is nothing to the right to move to.
        awaitPlaced()
        // Only if focus is still here. Home takes focus itself as it arrives,
        // and moving right from there as well stepped off its first card onto
        // the second.
        if (open) focusManager.moveFocus(FocusDirection.Right)
    }

    Box(Modifier.fillMaxSize().background(Tokens.bg)) {
        Box(
            Modifier
                .fillMaxSize()
                // Nothing on the right: a shelf runs off the screen's edge, as
                // YouTube's does, and a tab that needs a margin there takes it
                // itself — see where `App` draws the list tabs.
                .padding(start = CONTENT_START),
        ) {
            CompositionLocalProvider(LocalReturnToTop provides returnToTop) { content() }
        }

        // The wash behind the open rail, and only while it is open: closed,
        // the glyphs sit on the page's own ground.
        AnimatedVisibility(open, enter = fadeIn(tween(RAIL_MILLIS)), exit = fadeOut(tween(RAIL_MILLIS))) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(RAIL_START + RAIL_OPEN + RAIL_FADE)
                    .background(
                        Brush.horizontalGradient(
                            0f to Tokens.bg,
                            0.7f to Tokens.bg.copy(alpha = 0.92f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }

        Column(
            Modifier
                .fillMaxHeight()
                .padding(start = RAIL_START, top = TV_SAFE_VERTICAL, bottom = TV_SAFE_VERTICAL)
                .width(width)
                .onFocusChanged { open = it.hasFocus }
                // Redirected on the way in rather than restored: what is right
                // is always the tab somebody is on, not the row they last
                // pressed. `focusRestorer` with a fallback was tried first and
                // measured on the television — with nothing to restore it let
                // the nearest row win, and moving left from a shelf landed on
                // Search.
                .focusProperties { onEnter = { requesters.getValue(current).requestFocus() } }
                .focusGroup(),
            // Apart by more than a rim's width: the focused row's rim and the
            // selected row's white pill touched at YouTube's own pitch, and two
            // white shapes meeting read as one.
            verticalArrangement = Arrangement.spacedBy(RAIL_GAP, Alignment.CenterVertically),
        ) {
            RailItem(
                label = strings.search,
                selected = false,
                open = open,
                onClick = onSearch,
            ) { tint -> Icon(SearchIcon, null, tint = tint, modifier = Modifier.size(GLYPH)) }

            Tab.entries.forEach { tab ->
                RailItem(
                    label = tabLabel(tab, strings),
                    selected = tab == current,
                    open = open,
                    onClick = {
                        // The tab already showing, picked again, goes back to
                        // its top and takes focus there itself; moving right
                        // from the rail as well would step off its first card.
                        if (tab != current || !returnToTop.request()) {
                            onSelect(tab)
                            picked++
                        }
                    },
                    modifier = Modifier.focusRequester(requesters.getValue(tab)),
                ) { tint ->
                    TabGlyph(tab, selected = tab == current, tint = tint, modifier = Modifier.size(GLYPH))
                }
            }
        }
    }
}

/**
 * The rail's tab picked again while it is showing.
 *
 * Every TV app answers that by going back to the tab's top — and on Home, by
 * asking again — the way a phone's tab bar does for a second tap. The tab is
 * composed under the rail and owns its scroll, so the rail says only *that* it
 * happened, and each tab says what its top is through [ReturnsToTop].
 */
class ReturnToTop {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** False when nothing on the tab answers — still loading, say — and the rail hands focus on. */
    fun request(): Boolean = requests.subscriptionCount.value > 0 && requests.tryEmit(Unit)

    internal suspend fun collect(action: suspend () -> Unit): Nothing = requests.collect { action() }
}

/** The rail's [ReturnToTop], for the tab drawn beside it; null on every page and on a phone. */
val LocalReturnToTop = staticCompositionLocalOf<ReturnToTop?> { null }

/**
 * This tab's answer to its rail item being picked again: [scrollToTop], then
 * focus to [focus] once what was scrolled to has been placed.
 */
@Composable
fun ReturnsToTop(focus: FocusRequester, scrollToTop: suspend () -> Unit) {
    val returns = LocalReturnToTop.current ?: return
    val scroll by rememberUpdatedState(scrollToTop)
    LaunchedEffect(returns) {
        returns.collect {
            scroll()
            awaitPlaced()
            runCatching { focus.requestFocus(FocusDirection.Enter) }
        }
    }
}

/**
 * One row of the rail: a glyph, and its label once the rail is open.
 *
 * Selected is the inverted surface with the ink following it, the app's one way
 * of saying "this is on" — never a brighter shade of the same pane.
 */
@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable (tint: Color) -> Unit,
) {
    val ink = if (selected) Tokens.invertText else Tokens.text
    Row(
        modifier
            .height(ITEM_HEIGHT)
            .fillMaxWidth()
            // Before the clip, so what rises is the painted pill and its rim is
            // drawn outside the clip rather than shaved by it.
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Tokens.invertBg else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ITEM_HEIGHT), contentAlignment = Alignment.Center) { glyph(ink) }
        if (open) {
            Spacer(Modifier.width(Space.xs))
            Text(
                text = label,
                color = ink,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Space.lg),
            )
        }
    }
}

// The rail's geometry is YouTube's TV app's, measured off a screenshot of it on
// the household's Chromecast: glyphs centred 39dp from the screen's left edge,
// one every 40dp or so, and the shelves starting at 78dp. That is inside the
// 48dp overscan margin Android's guidance gives — and it is what the app every
// television in the house already shows, on sets that do not overscan.
private val ITEM_HEIGHT: Dp = 40.dp
private val GLYPH: Dp = 20.dp

/** Between two rows of the rail. */
private val RAIL_GAP: Dp = 12.dp

/** A glyph's circle: the rail closed is exactly one item wide. */
private val RAIL_CLOSED: Dp = ITEM_HEIGHT
private val RAIL_OPEN: Dp = 200.dp

/** From the screen's edge to the rail, so a glyph's centre is at 39dp. */
private val RAIL_START: Dp = 19.dp

/**
 * From the screen's edge to the tab's content. 62, so that the room a shelf
 * leaves for a focused card's rise puts the first card at YouTube's 78dp.
 */
private val CONTENT_START: Dp = 62.dp

/** How far past the open rail its wash runs before it is gone. */
private val RAIL_FADE: Dp = 120.dp

/** Inside the 150–300ms band, like every other movement in this app. */
private const val RAIL_MILLIS = 200

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvShellPreview() = MytubeTheme {
    CompositionLocalProvider(LocalTelevision provides true) {
        TvShell(Tab.Home, {}, {}) { Box(Modifier.fillMaxSize()) }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvShellVietnamesePreview() = MytubeTheme {
    CompositionLocalProvider(
        LocalTelevision provides true,
        LocalStrings provides VietnameseStrings,
    ) {
        TvShell(Tab.Settings, {}, {}) { Box(Modifier.fillMaxSize()) }
    }
}
