package com.mytube.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.FeedSkeleton
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.ScreenTitle
import com.mytube.app.ui.shell.TabRefreshIndicator
import com.mytube.app.ui.shell.detailContentPadding
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.shell.tabContentPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One chip's feed.
 *
 * A single column of large cards, which is what the web app's phone layout does
 * and what the shape of the content wants: a thumbnail is the only thing anybody
 * reads at a glance, and two per row on a phone makes it too small to be that.
 *
 * The chip row that selects between feeds is not here — it is pinned in the bar,
 * above the pager these pages live in — so each page is just its own feed.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    mediaBaseUrl: String,
    /**
     * Hoisted, so the reading position survives a swipe to another chip and
     * back. A pager page is disposed when it scrolls out of view, so a
     * page-local `rememberLazyListState()` would reset to the top; the state
     * belongs to the chip, which outlives the page, so it is owned above.
     */
    listState: LazyListState,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    /** The setup screen, from the "set the address" button when no server is set. */
    onOpenServer: () -> Unit,
    /**
     * Non-null when this feed is a standalone page (the Live page off More)
     * rather than a page of the home pager: it then carries a back arrow and a
     * title, and sits a top-bar's height down rather than below the chip row.
     */
    onBack: (() -> Unit)? = null,
    title: String = "",
    /**
     * Opens the sheet asking which collections this video belongs in.
     *
     * The screen does not answer it: the sheet is hoisted to `App.kt` because
     * several screens open the same one, and because it must be drawn last — a
     * [com.mytube.app.ui.shell.GlassSheet] is an ordinary child of the root
     * `Box` rather than a popup layer, so where it is written is where it sits.
     */
    onSaveToPlaylist: (Video) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        listState = listState,
        onOpenVideo = onOpenVideo,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onSaveVideo = onSaveToPlaylist,
        onNotInterested = viewModel::notInterested,
        onOpenChannel = onOpenChannel,
        onOpenServer = onOpenServer,
        onBack = onBack,
        title = title,
    )
}

/**
 * The feed, as plain values.
 *
 * Split from [HomeScreen] so it can be previewed and rendered in a test without
 * a ViewModel — the same split every screen here uses.
 */
@Composable
fun HomeContent(
    state: HomeState,
    mediaBaseUrl: String,
    onOpenVideo: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveVideo: (Video) -> Unit = {},
    onNotInterested: (Video) -> Unit = {},
    onOpenChannel: (String) -> Unit = {},
    onOpenServer: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    title: String = "",
    // Last, and with a default, so the previews below can keep calling this
    // positionally without caring that a chip's scroll position is hoisted.
    listState: LazyListState = rememberLazyListState(),
) {
    val strings = LocalStrings.current
    // A pager page sits below the chip row the shell pins; a standalone page has
    // no chip row, only the top bar, and owns its own backdrop and back arrow.
    val standalone = onBack != null
    val contentPadding = if (standalone) detailContentPadding() else tabContentPadding()
    val root = if (standalone) Modifier.fillMaxSize().glassSource() else Modifier.fillMaxSize()

    Box(root) {
    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is HomeState.Loading -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding())
                    // A `Column` that runs out of room does not clip its
                    // overflow — it draws the children at their natural size, one
                    // on top of the other. `FeedSkeleton`'s cards fit an
                    // emulator's taller window and not a real phone's, so the
                    // last card stacked on the one before it; the scroll gives it
                    // the room.
                    .verticalScroll(rememberScrollState()),
            ) {
                FeedSkeleton()
            }

            is HomeState.NeedsServer -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.noServerTitle, color = Tokens.text)
                    Spacer(Modifier.height(8.dp))
                    GlassButton(strings.setTheAddress, onOpenServer, primary = true)
                }
            }

            is HomeState.Failed -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.couldNotReach, color = Tokens.text)
                    Spacer(Modifier.height(4.dp))
                    Text(current.message, color = Tokens.text2, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    GlassButton(strings.tryAgain, onRetry, primary = true)
                }
            }

            is HomeState.Ready -> Feed(
                current, listState, mediaBaseUrl, strings,
                onOpenVideo, onRetry, onLoadMore,
                onSaveVideo, onNotInterested, onOpenChannel,
                contentPadding, title,
            )
        }
    }
        if (onBack != null) DetailBack(onBack, strings.back)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Feed(
    state: HomeState.Ready,
    listState: LazyListState,
    mediaBaseUrl: String,
    strings: Strings,
    onOpenVideo: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveVideo: (Video) -> Unit,
    onNotInterested: (Video) -> Unit,
    onOpenChannel: (String) -> Unit,
    contentPadding: PaddingValues,
    title: String,
) {
    // Asked when the end comes into view, not when the last item is composed.
    //
    // `derivedStateOf` is what keeps this from recomposing the whole screen on
    // every pixel of scroll: it recomputes on every frame but only emits when
    // the boolean flips. Everything read here comes from `layoutInfo`, which is
    // snapshot state and grows with the list — comparing against a captured
    // `lastIndex` froze the end at the first page and the feed stopped at 48.
    val atEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { atEnd }.collect { if (it) onLoadMore() }
    }

    // A finished pull-to-refresh returns the feed to the top.
    //
    // Reported on the Missed feed: pulling to refresh fetched a changed list but
    // left the reader wherever they had scrolled to, now stranded mid-list among
    // new rows. The refresh keeps `Ready` on screen (see `HomeViewModel.load`), so
    // the list does not reset itself — this does, on the `true → false` edge of
    // the refreshing flag, which only a deliberate pull raises. The jump is
    // instant, not animated: a scroll animation back up past the new rows reads
    // as the list lurching on its own.
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.refreshing) {
        if (wasRefreshing && !state.refreshing) listState.scrollToItem(0)
        wasRefreshing = state.refreshing
    }

    // Material3's own pull-to-refresh rather than the web app's hand-tuned
    // curve. The indicator has to clear the top bar, which floats over this
    // list — `indicator = {}` removes it rather than moving it, which refreshed
    // the feed with nothing on screen saying so.
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            // Below the chips, not behind them: this tab is the one with a
            // second row pinned under the bar.
            TabRefreshIndicator(refreshState, state.refreshing)
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The list scrolls under both bars — that is what makes them feel
            // like glass over content — but its content must start below the top
            // bar (which carries the chip row on Home) and end above the tab bar.
            // The shared arithmetic, not a copy of it; a standalone page passes
            // the detail padding instead, which leaves no room for a chip row.
            contentPadding = contentPadding,
        ) {
            if (title.isNotEmpty()) {
                item(key = "title") { ScreenTitle(title) }
            }

            if (state.videos.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(Space.xl), Alignment.Center) {
                        Text(strings.nothingHere, color = Tokens.text2)
                    }
                }
            }

            items(state.videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    mediaBaseUrl = mediaBaseUrl,
                    strings = strings,
                    onClick = { onOpenVideo(video.id) },
                    onSave = { onSaveVideo(video) },
                    onNotInterested = { onNotInterested(video) },
                    onOpenChannel = { onOpenChannel(video.channel.id) },
                )
            }

            if (state.loadingMore) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(Space.lg), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

// --- previews ---------------------------------------------------------------
//
// Every state, not just the one that works. The empty and failed cases are the
// ones a person meets when something is wrong, which is precisely when a badly
// laid out screen is least welcome — and they are the states hardest to reach on
// a device, so they are the ones most likely to go unseen without these.

private fun sampleVideo(id: String, title: String, seconds: Int, views: Long, watched: Double = 0.0) =
    Video(
        id = id,
        title = title,
        channel = Channel(
            id = "c",
            name = "CBC News",
            handle = "@CBCNews",
            avatarPath = "channels/c/avatar.jpg",
            subscribed = true,
        ),
        durationSeconds = seconds,
        viewCount = views,
        publishedAt = "2026-08-28T05:35:52Z",
        thumbnailPath = "thumbnails/$id.jpg",
        watchedFraction = watched,
    )

private fun sampleReady() = HomeState.Ready(
    videos = listOf(
        sampleVideo("a", "Canadian describes what he saw as catastrophic floods hit Nepal", 451, 157_000),
        sampleVideo("b", "A short one", 42, 980),
        sampleVideo("c", "Something over an hour long, with a title that runs on and has to be cut", 4_231, 4_730_000, 0.3),
    ),
    nextPageToken = "t1",
)

@Preview
@Composable
private fun HomeReadyPreview() = MytubeTheme {
    HomeContent(sampleReady(), "", {}, {}, {})
}

@Preview
@Composable
private fun HomeVietnamesePreview() = MytubeTheme {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        HomeContent(sampleReady(), "", {}, {}, {})
    }
}

@Preview
@Composable
private fun HomeEmptyPreview() = MytubeTheme {
    HomeContent(HomeState.Ready(videos = emptyList(), nextPageToken = ""), "", {}, {}, {})
}

@Preview
@Composable
private fun HomeNeedsServerPreview() = MytubeTheme {
    HomeContent(HomeState.NeedsServer, "", {}, {}, {})
}

@Preview
@Composable
private fun HomeFailedPreview() = MytubeTheme {
    HomeContent(HomeState.Failed("gateway answered 502"), "", {}, {}, {})
}

@Preview
@Composable
private fun HomeLoadingPreview() = MytubeTheme {
    HomeContent(HomeState.Loading, "", {}, {}, {})
}
