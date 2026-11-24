package com.mytube.app.ui.history

import androidx.compose.runtime.remember
import com.mytube.app.ui.home.videoMenu
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import com.mytube.app.ui.tv.fullWidthItem
import com.mytube.app.ui.tv.headingItem
import com.mytube.app.ui.tv.TvGridCard
import com.mytube.app.ui.tv.TvGrid
import com.mytube.app.ui.shell.LocalTelevision
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.theme.Tokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.ScreenTitle
import com.mytube.app.ui.shell.TabRefreshIndicator
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.detailContentPadding
import com.mytube.app.ui.shell.tabContentPadding
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.theme.MytubeTheme
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * What this member has watched, newest first.
 *
 * The same card as the feed, deliberately. They are the same kind of thing, and
 * the server charter makes the argument for the equivalent pair of pages in the
 * web app: any difference between two lists of videos is one the viewer has to
 * learn for no reason.
 *
 * ## A tab, and a page when it is not
 *
 * It is the second tab now — the shelf the household reaches for most after Home
 * — so it is drawn as a tab: a [ScreenTitle] at the top, the tab chrome's
 * padding, its scroll hoisted in `App.kt` so it keeps its place while another
 * tab is visited, and no back arrow. Like [com.mytube.app.ui.playlist.PlaylistsScreen],
 * it keeps the page shape too: pass a non-null `onBack` and it is a page with
 * the detail padding and the back arrow, for a caller that opens it from a menu.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    mediaBaseUrl: String,
    listState: LazyListState,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    /** Opens the sheet asking which collections this belongs in. See `App.kt`. */
    onSaveToPlaylist: (Video) -> Unit,
    /** Non-null makes it a page with a back arrow; null (a tab) draws neither. */
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HistoryContent(
        state = state,
        onBack = onBack,
        onOpenChannel = onOpenChannel,
        mediaBaseUrl = mediaBaseUrl,
        listState = listState,
        onOpenSettings = onOpenSettings,
        onOpenVideo = onOpenVideo,
        onRefresh = viewModel::refresh,
        onSaveVideo = onSaveToPlaylist,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    state: HistoryState,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    /** Non-null makes it a page with a back arrow; null (a tab) draws neither. */
    onBack: (() -> Unit)? = null,
    /**
     * Open the channel behind a row's avatar.
     *
     * It was not passed here, so every avatar in this list opened the video —
     * the third screen to make the same mistake, each written after the last one
     * fixed it. The avatar is the one part of a card that *is* the channel, and
     * pressing it and getting the video is what people describe as "the avatar
     * does nothing": something did happen and it was not what they aimed at.
     */
    onOpenChannel: (String) -> Unit = {},
    onSaveVideo: (Video) -> Unit = {},
) {
    val strings = LocalStrings.current

    // Recorded, so the miniplayer floating over this page is glass here too.
    Box(Modifier.fillMaxSize().glassSource()) {
    TabScaffold(
        loading = state is HistoryState.Loading,
        needsServer = state is HistoryState.NeedsServer,
        failure = (state as? HistoryState.Failed)?.message.orEmpty(),
        noServerTitle = strings.noServerTitle,
        setTheAddress = strings.setTheAddress,
        couldNotReach = strings.couldNotReach,
        tryAgain = strings.tryAgain,
        onOpenSettings = onOpenSettings,
        onRetry = onRefresh,
    ) {
        val ready = state as? HistoryState.Ready ?: return@TabScaffold

        val refreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = ready.refreshing,
            onRefresh = onRefresh,
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = { TabRefreshIndicator(refreshState, ready.refreshing) },
        ) {
            // A grid of Home's cards on a television, as the playlists' pages
            // are — see `TvGrid`.
            if (LocalTelevision.current) {
                TvGrid(Modifier.fillMaxSize()) {
                    headingItem(key = "title") { ScreenTitle(strings.historyTitle) }
                    if (ready.videos.isEmpty()) {
                        fullWidthItem(key = "empty") {
                            EmptyState(strings.noHistory, strings.noHistoryDetail)
                        }
                    }
                    items(ready.videos, key = { it.id }) { video ->
                        TvGridCard(
                            video = video,
                            mediaBaseUrl = mediaBaseUrl,
                            strings = strings,
                            onClick = { onOpenVideo(video.id) },
                            opening = false,
                            menu = remember(video, strings) { videoMenu(strings, onSave = { onSaveVideo(video) }) },
                        )
                    }
                }
                return@PullToRefreshBox
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // The tab chrome when it is a tab, the detail chrome (clearing
                // the back arrow) when a menu opened it as a page.
                contentPadding = if (onBack == null) tabContentPadding() else detailContentPadding(),
            ) {
                item(key = "title") { ScreenTitle(strings.historyTitle) }

                if (ready.videos.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(strings.noHistory, strings.noHistoryDetail)
                    }
                }

                items(ready.videos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        mediaBaseUrl = mediaBaseUrl,
                        strings = strings,
                        onClick = { onOpenVideo(video.id) },
                        onSave = { onSaveVideo(video) },
                        onOpenChannel = { onOpenChannel(video.channel.id) },
                        // No "not interested" here. History is a record of what
                        // was watched, and telling the ranker off from a list of
                        // things somebody chose to watch is the wrong signal in
                        // the wrong place.
                    )
                }
            }
        }
    }

    if (onBack != null) DetailBack(onBack, strings.back)
    }
}

// --- previews ---------------------------------------------------------------

private fun sample(id: String, title: String, watched: Double) = Video(
    id = id,
    title = title,
    channel = Channel(
        id = "c",
        name = "Adam Savage's Tested",
        handle = "@tested",
        avatarPath = "channels/c/avatar.jpg",
    ),
    durationSeconds = 1397,
    viewCount = 78_200,
    publishedAt = "2026-08-28T05:35:52Z",
    thumbnailPath = "thumbnails/$id.jpg",
    watchedFraction = watched,
)

@Preview
@Composable
private fun HistoryPreview() {
    MytubeTheme {
        HistoryContent(
            state = HistoryState.Ready(
                listOf(
                    sample("a", "The Chair of Theseus", 0.42),
                    sample("b", "Adam Savage's One Day Builds", 0.98),
                ),
            ),
            mediaBaseUrl = "",
            onBack = {},
            onOpenSettings = {},
            onOpenVideo = {},
            onRefresh = {},
        )
    }
}

/**
 * Empty, which is the state a fresh install opens on — and the one that reads as
 * a fault if it is drawn badly.
 */
@Preview
@Composable
private fun HistoryEmptyPreview() {
    MytubeTheme {
        HistoryContent(
            state = HistoryState.Ready(emptyList()),
            mediaBaseUrl = "",
            onBack = {},
            onOpenSettings = {},
            onOpenVideo = {},
            onRefresh = {},
        )
    }
}

@Preview
@Composable
private fun HistoryVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        MytubeTheme {
            HistoryContent(
                state = HistoryState.Ready(emptyList()),
                mediaBaseUrl = "",
                onBack = {},
                onOpenSettings = {},
                onOpenVideo = {},
                onRefresh = {},
            )
        }
    }
}
