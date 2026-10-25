package com.mytube.app.ui.tv

import androidx.compose.ui.unit.Dp
import com.mytube.app.ui.shell.MenuAction
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.domain.model.Video
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import com.mytube.app.ui.shell.skeletonShade
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.shell.FOCUS_MAX_OUTSET

/**
 * A page of cards on a television, laid out as a grid.
 *
 * What the playlists tab, a playlist and the saved shelf become there: the
 * phone draws them as a column of rows somebody scans with a thumb, and on a
 * 960dp screen that column is one picture per screenful. A grid of shelf-sized
 * cards is three across — the household asked for these pages to look like
 * Home, and Home's cards are what these are.
 *
 * Three across on the television, at about a shelf card's size: as many columns
 * as fit cards of at least [GRID_CARD_MIN]. The shelf's own 264dp was the first
 * minimum, and with the gaps counted only two fitted, at four hundred dp each.
 * The padding is the room a focused card's rise needs, or the grid's clip
 * shaves the cards at its edges.
 *
 * Scrolled the television's way, as Home is: the focused row is held a third
 * of the way down. A page's heading stays whole through [headingItem].
 */
@Composable
fun TvGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    // On the playlists tab, the rail's item picked again.
    ReturnsToTop(focus) { state.scrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_CARD_MIN),
        modifier = modifier.focusRequester(focus),
        state = state,
        contentPadding = PaddingValues(FOCUS_MAX_OUTSET),
        horizontalArrangement = Arrangement.spacedBy(TV_CARD_GAP),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        content = content,
    )
}

/** A row of the grid that is not a card: an empty state, the next page coming. */
fun LazyGridScope.fullWidthItem(key: Any, content: @Composable LazyGridItemScope.() -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)

/**
 * The page's heading, across the grid: focus on anything in it brings the whole
 * heading into view, not just the control.
 *
 * The grid holds whatever is focused a third of the way down, and a channel's
 * subscribe button held there put the channel's avatar and name above the top
 * of the screen — reported as scrolling up to the button and never seeing who
 * the channel was. A heading is the top of its page, so the rectangle it asks
 * the grid for is its own, from its first line; the grid cannot scroll above
 * its start, and it stops at the top. Everything that sits over the cards
 * belongs in this one item — sorts too — or the item under it is a second
 * heading, and that one's top is the one held a third of the way down.
 */
// `BringIntoViewResponder` is experimental in Compose Foundation 1.11, and it is
// the one way for a child to change what its parent scrolls to show.
@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.headingItem(key: Any, content: @Composable LazyGridItemScope.() -> Unit) =
    fullWidthItem(key) {
        val whole = remember { WholeHeading() }
        Column(Modifier.onSizeChanged { whole.height = it.height }.bringIntoViewResponder(whole)) {
            content()
        }
    }

/** Asks for its own top-to-bottom, across whatever width the focused control was. */
@OptIn(ExperimentalFoundationApi::class)
private class WholeHeading : BringIntoViewResponder {
    var height = 0

    override fun calculateRectForParent(localRect: Rect): Rect =
        Rect(localRect.left, 0f, localRect.right, maxOf(localRect.bottom, height.toFloat()))

    // Nothing of its own scrolls; the grid does, with the rectangle above.
    override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
}

/**
 * A video's card in a [TvGrid]: a shelf card with its meta lines, filling its
 * cell. The four pages of videos draw this one, so a card tuned on one is tuned
 * on all of them.
 */
@Composable
fun TvGridCard(
    video: Video,
    mediaBaseUrl: String,
    strings: Strings,
    onClick: () -> Unit,
    /** See [TvVideoCard]. */
    opening: Boolean,
    /** See [TvThumbnail]. */
    menu: List<MenuAction>,
) = TvVideoCard(
    video = video,
    mediaBaseUrl = mediaBaseUrl,
    strings = strings,
    onClick = onClick,
    withMeta = true,
    opening = opening,
    menu = menu,
    width = Dp.Unspecified,
)

/** The narrowest a grid card may be; see [TvGrid] for why it is not the shelf's 264dp. */
private val GRID_CARD_MIN = 240.dp

/**
 * A grid page still loading: rows of three card skeletons at the grid's own
 * sizes. The phone's feed skeleton is a column of full-width pictures, and on a
 * 960dp television each one was a grey block the height of the screen.
 */
@Composable
fun TvGridSkeleton(rows: Int = 2) {
    val shade = skeletonShade()
    Column(
        Modifier.fillMaxWidth().padding(FOCUS_MAX_OUTSET),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(TV_CARD_GAP)) {
                repeat(GRID_SKELETON_COLUMNS) { TvCardSkeleton(shade, Modifier.weight(1f)) }
            }
        }
    }
}

/** The columns a television grid shows, which its skeleton draws. */
private const val GRID_SKELETON_COLUMNS = 3
