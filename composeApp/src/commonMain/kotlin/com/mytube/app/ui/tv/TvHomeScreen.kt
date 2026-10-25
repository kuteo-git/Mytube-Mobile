package com.mytube.app.ui.tv

import com.mytube.app.ui.shell.SkeletonLine
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.mytube.app.ui.home.videoMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.ui.home.Radius
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.FOCUS_MAX_OUTSET
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.LocalTelevision
import com.mytube.app.ui.shell.TV_SAFE_VERTICAL
import com.mytube.app.ui.shell.skeletonShade
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun TvHomeScreen(
    viewModel: TvHomeViewModel,
    mediaBaseUrl: String,
    onOpenVideo: (Video) -> Unit,
    onOpenSettings: () -> Unit,
    /** A long press of OK on a card — see `TvThumbnail`. */
    onSaveToPlaylist: (Video) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TvHomeContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onOpenVideo = onOpenVideo,
        onOpenSettings = onOpenSettings,
        onSaveToPlaylist = onSaveToPlaylist,
        onRetry = viewModel::refresh,
        onShelfShown = viewModel::ensureLoaded,
        onShelfEnd = viewModel::loadMore,
        onRetryShelf = viewModel::retry,
        onCatchUp = viewModel::catchUp,
    )
}

@Composable
fun TvHomeContent(
    state: TvHomeState,
    mediaBaseUrl: String,
    onOpenVideo: (Video) -> Unit,
    onOpenSettings: () -> Unit,
    onSaveToPlaylist: (Video) -> Unit,
    onRetry: () -> Unit,
    onShelfShown: (ShelfKind) -> Unit,
    onShelfEnd: (ShelfKind) -> Unit,
    onRetryShelf: (ShelfKind) -> Unit,
    /** Ask again with the shelves left up — see [TvHomeViewModel.catchUp]. */
    onCatchUp: () -> Unit,
) {
    val strings = LocalStrings.current
    when (state) {
        is TvHomeState.Loading -> ShelvesSkeleton()

        is TvHomeState.NeedsServer -> TvMessage(
            title = strings.noServerTitle,
            detail = "",
            actionLabel = strings.setTheAddress,
            onAction = onOpenSettings,
        )

        is TvHomeState.Failed -> TvMessage(
            title = strings.couldNotReach,
            detail = state.message,
            actionLabel = strings.tryAgain,
            onAction = onRetry,
        )

        is TvHomeState.Ready -> {
            if (state.shelves.none { it.shows }) {
                TvMessage(
                    title = strings.nothingHere,
                    detail = "",
                    actionLabel = strings.tryAgain,
                    onAction = onRetry,
                )
            } else {
                Shelves(
                    shelves = state.shelves,
                    mediaBaseUrl = mediaBaseUrl,
                    strings = strings,
                    onOpenVideo = onOpenVideo,
                    onSaveToPlaylist = onSaveToPlaylist,
                    onShelfShown = onShelfShown,
                    onShelfEnd = onShelfEnd,
                    onRetryShelf = onRetryShelf,
                    onCatchUp = onCatchUp,
                )
            }
        }
    }
}

@Composable
private fun Shelves(
    shelves: List<Shelf>,
    mediaBaseUrl: String,
    strings: Strings,
    onOpenVideo: (Video) -> Unit,
    onSaveToPlaylist: (Video) -> Unit,
    onShelfShown: (ShelfKind) -> Unit,
    onShelfEnd: (ShelfKind) -> Unit,
    onRetryShelf: (ShelfKind) -> Unit,
    onCatchUp: () -> Unit,
) {
    // One scroll position and one card left on per shelf, held above the
    // column.
    //
    // A shelf is an item of a lazy column, and a `remember` inside a lazy item
    // is a cache: scroll a shelf off the top and back and it would be at its
    // first card again, with focus nowhere near the card it left on.
    val rows = remember { mutableMapOf<String, RowMemory>() }
    // A shelf that has emptied while focus is in it — Missed, once its one
    // video is watched — stays until focus leaves, for [keptInPlace]'s reason.
    val shown = shelves.filter { it.shows || rows[it.kind.key]?.holding == true }
    val columnState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val firstShelf = remember { FocusRequester() }
    // Focus on arrival. Coming back from the player is not an arrival — the
    // page never left — and the app hands focus back to the card that was
    // pressed: see `FocusMemory`. Asking here as well would put it on the first
    // card for a frame and scroll the page up to meet it.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Home picked again on the rail: back to the first card of the first
    // shelf, every shelf at its start, and asked again — YouTube's TV app does
    // all three.
    ReturnsToTop(focus = firstShelf) {
        onCatchUp()
        // Asked, not awaited: a shelf scrolled out of the column has no layout,
        // and `scrollToItem` on it waits for one that never comes.
        rows.values.forEach { it.forget(); it.scroll.requestScrollToItem(0) }
        columnState.scrollToItem(0)
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusRestorer(),
        state = columnState,
        contentPadding = PaddingValues(top = SHELVES_TOP, bottom = TV_SAFE_VERTICAL),
        verticalArrangement = Arrangement.spacedBy(SHELF_GAP),
    ) {
        itemsIndexed(shown, key = { _, shelf -> shelf.kind.key }) { index, shelf ->
            LaunchedEffect(shelf.kind.key) { onShelfShown(shelf.kind) }
            ShelfRow(
                shelf = shelf,
                title = shelfTitle(shelf.kind, strings),
                cardWidth = shelfCardWidth(index),
                memory = rows.getOrPut(shelf.kind.key) { RowMemory() },
                mediaBaseUrl = mediaBaseUrl,
                strings = strings,
                onOpenVideo = onOpenVideo,
                onSaveToPlaylist = onSaveToPlaylist,
                onEnd = { onShelfEnd(shelf.kind) },
                onRetry = { onRetryShelf(shelf.kind) },
                modifier = if (index == 0) Modifier.focusRequester(firstShelf) else Modifier,
            )
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    title: String,
    cardWidth: Dp,
    memory: RowMemory,
    mediaBaseUrl: String,
    strings: Strings,
    onOpenVideo: (Video) -> Unit,
    onSaveToPlaylist: (Video) -> Unit,
    onEnd: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val rowState = memory.scroll
    Column(Modifier.fillMaxWidth()) {
        TvSectionTitle(title)
        when (shelf.load) {
            ShelfLoad.Waiting, ShelfLoad.Loading -> SkeletonCards(cardWidth)

            ShelfLoad.Failed -> Row(Modifier.padding(start = FOCUS_MAX_OUTSET, top = Space.sm)) {
                GlassButton(label = strings.tryAgain, onClick = onRetry)
            }

            ShelfLoad.Ready -> {
                // The next page is asked for a few cards before the end, read
                // from the row's own layout rather than from the index focus is
                // on: the two are the same thing on a remote, and this one is
                // already snapshot state.
                val count by rememberUpdatedState(shelf.videos.size)
                val end by rememberUpdatedState(onEnd)
                LaunchedEffect(rowState) {
                    snapshotFlow { rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .distinctUntilChanged()
                        .collect { last -> if (last >= count - LOAD_AHEAD) end() }
                }
                // The video focus is on, held above the lazy row's items so it
                // outlives a new answer that has dropped it — see [keptInPlace].
                val videos = keptInPlace(
                    items = shelf.videos,
                    focused = memory.lastVideo?.takeIf { memory.holding },
                    at = memory.lastIndex,
                    id = { it.id },
                )
                val current by rememberUpdatedState(videos)
                val save by rememberUpdatedState(onSaveToPlaylist)
                // A new answer that moves the card focus is on — Continue
                // watching puts the video just watched first — takes the row
                // with it. Focus follows the card by its key, but nothing
                // scrolled: measured, the focused card sat cut off at the
                // screen's left edge.
                LaunchedEffect(videos) {
                    if (!memory.holding) return@LaunchedEffect
                    val moved = videos.indexOfFirst { it.id == memory.lastId }
                    if (moved < 0 || moved == memory.lastIndex) return@LaunchedEffect
                    memory.focused(moved, memory.lastId)
                    rowState.animateScrollToItem(moved)
                }
                LazyRow(
                    state = rowState,
                    // The rise of a focused card, on every side, or the row's
                    // clip shaves the first card's left edge and every card's
                    // rim top and bottom.
                    contentPadding = PaddingValues(
                        horizontal = FOCUS_MAX_OUTSET,
                        vertical = FOCUS_MAX_OUTSET / 2,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TV_CARD_GAP),
                    modifier = modifier
                        .onFocusChanged { memory.holding = it.hasFocus }
                        .entersAt(
                            state = rowState,
                            index = { rowEntryIndex(current.map { it.id }, memory.lastId, memory.lastIndex) },
                            requester = { memory.cards[current.getOrNull(it)?.id] },
                        ),
                ) {
                    itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
                        val requester = remember { FocusRequester() }
                        DisposableEffect(video.id) {
                            memory.cards[video.id] = requester
                            onDispose { if (memory.cards[video.id] === requester) memory.cards.remove(video.id) }
                        }
                        // Remembered per video: a new list on every recomposition
                        // was a card that could never skip.
                        val menu = remember(video) { videoMenu(strings, onSave = { save(video) }) }
                        TvVideoCard(
                            video = video,
                            mediaBaseUrl = mediaBaseUrl,
                            strings = strings,
                            onClick = { onOpenVideo(video) },
                            withMeta = true,
                            opening = false,
                            menu = menu,
                            width = cardWidth,
                            modifier = Modifier
                                .focusRequester(requester)
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        memory.focused(index, video.id)
                                        memory.lastVideo = video
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A heading over a row of cards on a television: a shelf's, a search half's.
 *
 * 20sp. YouTube's TV app draws its shelf headings at 16 — measured off its
 * screenshot by the height of a capital — and beside 18sp card titles that read
 * as smaller than the cards they name; reported from the sofa as the headings
 * getting lost. The gap under it is still the measured one: the heading sits
 * about twenty dp above the pictures.
 */
@Composable
internal fun TvSectionTitle(title: String) {
    Text(
        text = title,
        color = Tokens.text,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = FOCUS_MAX_OUTSET, bottom = Space.sm),
    )
}

/**
 * A shelf's heading.
 *
 * A topic is named as the server names it, untranslated: it is the library's
 * own label, the way a channel's sort names are upstream's.
 */
internal fun shelfTitle(kind: ShelfKind, strings: Strings): String = when (kind) {
    ShelfKind.ContinueWatching -> strings.continueWatching
    ShelfKind.Missed -> strings.chipMissed
    ShelfKind.Live -> strings.chipLive
    ShelfKind.Recommended -> strings.shelfRecommended
    is ShelfKind.OfTopic -> kind.topic.name
}

/**
 * Cards still on their way, at the real card's measurements.
 *
 * The picture, then a box the height of two title lines and one the height of
 * two meta lines — the same boxes [TvVideoCard] draws — so the shelf does not
 * change height when the pictures land.
 *
 * Laid out unbounded, past the right of the screen. The first build fitted five
 * fixed-width cards into a row narrower than them: the last ones were squeezed,
 * and a squeezed `aspectRatio` box grew *tall* instead, which drew a grey slab
 * down the right of the screen and pushed the second row off the bottom.
 */
@Composable
private fun SkeletonCards(cardWidth: Dp) {
    val shade = skeletonShade()
    Row(
        Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .padding(horizontal = FOCUS_MAX_OUTSET, vertical = FOCUS_MAX_OUTSET / 2),
        horizontalArrangement = Arrangement.spacedBy(TV_CARD_GAP),
    ) {
        repeat(SKELETON_CARDS) { TvCardSkeleton(shade, Modifier.width(cardWidth)) }
    }
}

/**
 * One card still on its way, at [TvVideoCard]'s measurements — its picture, a
 * box the height of two title lines and one the height of two meta lines — so a
 * page does not change height when the cards land. Shared by the shelves and by
 * the grids.
 */
@Composable
internal fun TvCardSkeleton(shade: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radius.thumbnail))
                .background(shade),
        )
        Spacer(Modifier.height(TITLE_GAP))
        Column(Modifier.height(48.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            SkeletonLine(shade, 0.9f, 16.dp)
            SkeletonLine(shade, 0.6f, 16.dp)
        }
        Spacer(Modifier.height(Space.xs))
        Column(Modifier.height(38.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            SkeletonLine(shade, 0.5f, 12.dp)
            SkeletonLine(shade, 0.4f, 12.dp)
        }
    }
}

@Composable
private fun ShelvesSkeleton() {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().padding(top = SHELVES_TOP),
        verticalArrangement = Arrangement.spacedBy(SHELF_GAP),
    ) {
        // Titled like the first shelf a load always brings back, so the word
        // over the first row does not change when it arrives — and sized like
        // the shelves will be: the first large, the rest ordinary.
        Column {
            TvSectionTitle(strings.shelfRecommended)
            SkeletonCards(shelfCardWidth(0))
        }
        SkeletonCards(shelfCardWidth(1))
    }
}

/** Above the first shelf's heading. */
private val SHELVES_TOP = 32.dp

/**
 * Between one shelf's last line of text and the next shelf's heading, less the
 * room left for a focused card's rise — YouTube's TV app's 34dp, measured.
 */
private val SHELF_GAP = 20.dp

/** How many cards before a shelf's end its next page is asked for. */
private const val LOAD_AHEAD = 4

/** Enough to run off the right of the screen, which is what a shelf does. */
private const val SKELETON_CARDS = 5

// --- previews ---------------------------------------------------------------

private fun previewVideo(id: String, title: String) = Video(
    id = id,
    title = title,
    channel = Channel(id = "c$id", name = "Channel $id", handle = "", avatarPath = ""),
    durationSeconds = 754,
    viewCount = 12_400,
    publishedAt = "2026-10-20T08:00:00Z",
    thumbnailPath = "",
)

private val previewShelves = shelvesFrom(
    continueWatching = listOf(previewVideo("1", "Half way through this one")),
    missed = null,
    live = emptyList(),
    recommended = FeedPage(
        videos = (2..7).map { previewVideo("$it", "A video worth watching, number $it") },
        nextPageToken = "next",
    ),
    topics = listOf(Topic("Music", 40), Topic("Cooking", 0)),
)

@Composable
private fun preview(state: TvHomeState) = MytubeTheme {
    CompositionLocalProvider(LocalTelevision provides true) {
        Box(Modifier.fillMaxSize().background(Tokens.bg)) {
            TvHomeContent(state, "", {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvHomeReadyPreview() = preview(TvHomeState.Ready(previewShelves))

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvHomeLoadingPreview() = preview(TvHomeState.Loading)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvHomeEmptyPreview() = preview(TvHomeState.Ready(emptyList()))

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvHomeFailedPreview() = preview(TvHomeState.Failed("gateway answered 502"))

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvHomeVietnamesePreview() = MytubeTheme {
    CompositionLocalProvider(
        LocalTelevision provides true,
        LocalStrings provides VietnameseStrings,
    ) {
        Box(Modifier.fillMaxSize().background(Tokens.bg)) {
            TvHomeContent(TvHomeState.Ready(previewShelves), "", {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
