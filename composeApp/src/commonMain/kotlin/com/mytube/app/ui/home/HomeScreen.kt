package com.mytube.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.mytube.app.ui.shell.FeedSkeleton
import com.mytube.app.ui.shell.TabRefreshIndicator
import com.mytube.app.ui.shell.tabContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.runtime.CompositionLocalProvider
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The feed.
 *
 * A single column of large cards, which is what the web app's phone layout does
 * and what the shape of the content wants: a thumbnail is the only thing anybody
 * reads at a glance, and two per row on a phone makes it too small to be that.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onOpenSettings = onOpenSettings,
        onOpenVideo = onOpenVideo,
        onSelectChip = viewModel::select,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onSaveVideo = viewModel::toggleSaved,
        onNotInterested = viewModel::notInterested,
        onOpenChannel = onOpenChannel,
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
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onSelectChip: (Chip) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveVideo: (Video) -> Unit = {},
    onNotInterested: (Video) -> Unit = {},
    onOpenChannel: (String) -> Unit = {},
) {
    val strings = LocalStrings.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is HomeState.Loading -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = tabContentPadding().calculateTopPadding()),
            ) { FeedSkeleton() }

            is HomeState.NeedsServer -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.noServerTitle, color = Tokens.text)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSettings) { Text(strings.setTheAddress) }
                }
            }

            is HomeState.Failed -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.couldNotReach, color = Tokens.text)
                    Spacer(Modifier.height(4.dp))
                    Text(current.message, color = Tokens.text2, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text(strings.tryAgain) }
                }
            }

            is HomeState.Ready -> Feed(
                current, mediaBaseUrl, strings,
                onSelectChip, onOpenVideo, onRetry, onLoadMore,
                onSaveVideo, onNotInterested, onOpenChannel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Feed(
    state: HomeState.Ready,
    mediaBaseUrl: String,
    strings: Strings,
    onSelectChip: (Chip) -> Unit,
    onOpenVideo: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveVideo: (Video) -> Unit,
    onNotInterested: (Video) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    // Asked when the end comes into view, not when the last item is composed.
    //
    // `derivedStateOf` is what keeps this from recomposing the whole screen on
    // every pixel of scroll: it recomputes on every frame but only *emits* when
    // the boolean flips.
    val atEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.videos.lastIndex - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { atEnd }.collect { if (it) onLoadMore() }
    }

    // Material3's own pull-to-refresh rather than the web app's hand-tuned
    // curve. That curve exists because a browser has no gesture of its own and
    // the constants were fitted to feel native; here the native one is available
    // and matching the rest of the phone is the whole point.
    //
    // The indicator has to clear the top bar, which floats over this list — and
    // `indicator = {}` does not move it out of the way, it removes it. That is
    // what this was, and the gesture then refreshed the feed with nothing on
    // screen ever saying so, which reads as a pull that did nothing.
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = { TabRefreshIndicator(refreshState, state.refreshing) },
    ) {
        // Pinned under the top bar rather than scrolled with the feed.
        //
        // It was the first item in the list, which is how a phone usually does
        // a header — and it is wrong here: the chips are the feed's *filter*,
        // and a filter that scrolls away means changing your mind costs a
        // journey back to the top. The web app pins it and so does this.
        ChipRow(
            chips = state.chips,
            selected = state.selected,
            onSelect = onSelectChip,
            modifier = Modifier
                .zIndex(1f)
                .background(Tokens.bg)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues()
                        .calculateTopPadding() + Size.topBar,
                    bottom = Space.md,
                ),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The list scrolls *under* both bars — that is what makes them feel
            // like glass over content rather than walls — but its content must
            // start below the top bar and end above the tab bar. Insets alone are
            // not enough: those describe the system's bars, not this app's.
            // The chip row is pinned, so the list has to start below it as well
            // as below the top bar. This is the fourth thing in this app to need
            // the bar's height and the second to need the row's — both live in
            // `Size`, and nothing here computes either.
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                    Size.topBar + Size.chipRow,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    Size.topBar,
            ),
        ) {
            // The rail scrolls away with the feed rather than sticking. It is a
            // shelf to glance at on the way past, not a fixture.
            item(key = "continue") {
                ContinueWatchingRail(
                    videos = state.continueWatching,
                    mediaBaseUrl = mediaBaseUrl,
                    onOpenVideo = onOpenVideo,
                    onSaveVideo = onSaveVideo,
                )
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
    chips = listOf(
        Chip.All,
        Chip.Live,
        Chip.Category(Topic("Gaming", 1166)),
        Chip.Category(Topic("Music", 787)),
        Chip.Category(Topic("News & Politics", 637)),
    ),
    continueWatching = listOf(
        sampleVideo("d", "Half way through this one", 900, 12_000, 0.42),
        sampleVideo("e", "And this one too", 1_800, 3_400, 0.7),
    ),
)

@Preview
@Composable
private fun HomeReadyPreview() = MytubeTheme {
    HomeContent(sampleReady(), "", {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun HomeVietnamesePreview() = MytubeTheme {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        HomeContent(sampleReady(), "", {}, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun HomeNeedsServerPreview() = MytubeTheme {
    HomeContent(HomeState.NeedsServer, "", {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun HomeFailedPreview() = MytubeTheme {
    HomeContent(HomeState.Failed("gateway answered 502"), "", {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun HomeLoadingPreview() = MytubeTheme {
    HomeContent(HomeState.Loading, "", {}, {}, {}, {}, {})
}
