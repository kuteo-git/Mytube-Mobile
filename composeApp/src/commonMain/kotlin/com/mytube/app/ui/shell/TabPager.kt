package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.theme.Tokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A row of tab labels over a horizontally swipeable content area, the way a
 * channel page reads on YouTube: swipe between the tabs, or tap one, and an
 * underline slides under the active label as the pages move.
 *
 * A component rather than a one-off because the shape is the app's, not any one
 * screen's — the labels take the app's text tokens, the active one the brighter,
 * and the underline is the brand red, the app's one accent, so a second caller
 * reads as the same control. The labels are laid out left to right at their own
 * width and are expected to fit the screen; the underline is placed from each
 * label's measured position, so a set that overflowed would need a scrolling
 * strip this does not yet have.
 */
@Composable
fun rememberTabPagerState(pageCount: Int, initialPage: Int = 0): PagerState =
    rememberPagerState(initialPage = initialPage) { pageCount }

/**
 * The top inset a page inside [CollapsingTabPager] must leave above its content,
 * so the first row starts below the header and tab strip. It shrinks as the
 * header collapses, which is what pulls the content up with the header. A page's
 * list reads it and adds it to its own top content padding; outside a collapsing
 * pager it is zero and adds nothing.
 */
val LocalCollapsingTopInset = compositionLocalOf { 0.dp }

/**
 * A tab pager with a header that collapses as the page scrolls, the way a channel
 * reads on YouTube: scroll up and the header (the avatar, the name, the subscribe
 * button) slides away under the top chrome until the tab strip reaches it and
 * stops — the strip then pins there, exactly where Home's chip row sits. Scroll
 * back down and once the list underneath can go no higher the header returns.
 *
 * It is one shared header over a pager, not a copy per page, so swiping between
 * the tabs keeps the same collapse. The pager fills the screen, so its lists
 * always reach the bottom; the header is drawn over them and the list's top
 * padding is [LocalCollapsingTopInset], which shrinks by the collapse so the
 * content rises with the header. The [NestedScrollConnection] spends the upward
 * scroll on the collapse first, consuming it so the list holds still until the
 * header is gone; the downward scroll reaches the list first, and only what it
 * cannot use at its own top brings the header back.
 *
 * @param header the collapsible block, measured to learn how far it may collapse.
 * @param topInset the fixed chrome above the header (the status inset and the row
 *   [DetailBack]'s arrow sits in); a solid band is drawn over it so the header
 *   vanishes behind it rather than over the status bar.
 */
@Composable
fun CollapsingTabPager(
    tabs: List<String>,
    state: PagerState,
    topInset: Dp,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    val density = LocalDensity.current
    // The header's height (the collapse distance) and the strip's, both measured.
    var headerPx by remember { mutableIntStateOf(0) }
    var stripPx by remember { mutableIntStateOf(0) }
    // How far the header is collapsed, 0 (open) to headerPx (gone).
    var offset by remember { mutableFloatStateOf(0f) }
    // A header that shrank (a shorter name, say) must not leave the offset past
    // its new end, or the strip would pin too high.
    LaunchedEffect(headerPx) {
        if (offset > headerPx) offset = headerPx.toFloat()
    }

    val connection = remember {
        object : NestedScrollConnection {
            // Scrolling the list up collapses the header first, consuming the
            // scroll so the list holds still until the header is spent.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy >= 0f) return Offset.Zero
                val old = offset
                offset = (old - dy).coerceIn(0f, headerPx.toFloat())
                return Offset(0f, old - offset)
            }

            // Scrolling back down, the list scrolls to its own top first; only
            // what it could not use (it is at the top) brings the header back.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy <= 0f) return Offset.Zero
                val old = offset
                offset = (old - dy).coerceIn(0f, headerPx.toFloat())
                return Offset(0f, old - offset)
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        // The content clears the header and strip when open, less as it collapses,
        // so the feed rises with the header. The pager itself fills the screen, so
        // the list below always reaches the bottom.
        val listTop = (topInset + with(density) { (headerPx + stripPx).toDp() } -
            with(density) { offset.toDp() }).coerceAtLeast(0.dp)
        CompositionLocalProvider(LocalCollapsingTopInset provides listTop) {
            HorizontalPager(
                state = state,
                modifier = Modifier.fillMaxSize(),
                // Each page keeps its own vertical scroll and loads its own data,
                // so the neighbours are not held composed — see [TabPager].
                beyondViewportPageCount = 0,
            ) { page -> content(page) }
        }

        // The header and strip over the feed, sliding up by the collapse. The
        // strip carries the page's own colour so the feed passing under it is
        // hidden; the band above does the same over the status and arrow row, so
        // the header vanishes behind it rather than across the clock. The arrow
        // ([DetailBack]) is drawn over all of this by the caller.
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -offset },
        ) {
            Spacer(Modifier.height(topInset))
            Box(Modifier.onSizeChanged { headerPx = it.height }) { header() }
            Box(
                Modifier
                    .background(Tokens.bg)
                    .onSizeChanged { stripPx = it.height },
            ) { TabStrip(tabs, state) }
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(topInset)
                .background(Tokens.bg),
        )
    }
}

@Composable
private fun TabStrip(tabs: List<String>, state: PagerState) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Each label's left edge and width, in the strip's own pixels, captured as
    // it is laid out. The underline is interpolated between two of these, so it
    // follows a half-finished swipe rather than jumping on settle.
    val lefts = remember { mutableStateMapOf<Int, Float>() }
    val widths = remember { mutableStateMapOf<Int, Float>() }

    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                val selected = state.currentPage == index
                Text(
                    text = label,
                    color = if (selected) Tokens.text else Tokens.text2,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .onGloballyPositioned {
                            lefts[index] = it.positionInParent().x
                            widths[index] = it.size.width.toFloat()
                        }
                        .pressable { scope.launch { state.animateScrollToPage(index) } }
                        .padding(horizontal = Space.lg, vertical = 14.dp),
                )
            }
        }

        // The continuous page position: whole part is the tab the underline sits
        // under, the fraction is how far it has slid toward the next.
        val position = state.currentPage + state.currentPageOffsetFraction
        val from = position.toInt().coerceIn(0, tabs.lastIndex)
        val to = (from + 1).coerceAtMost(tabs.lastIndex)
        val t = (position - from).coerceIn(0f, 1f)
        val fromLeft = lefts[from]
        val fromWidth = widths[from]
        if (fromLeft != null && fromWidth != null) {
            val left = fromLeft + ((lefts[to] ?: fromLeft) - fromLeft) * t
            val width = fromWidth + ((widths[to] ?: fromWidth) - fromWidth) * t
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(left.roundToInt(), 0) }
                    .width(with(density) { width.toDp() })
                    .height(2.dp)
                    // The brand red, the app's one accent — the selection colour
                    // the chip row and the tab bar wear too.
                    .background(Tokens.brand),
            )
        }
    }
}
