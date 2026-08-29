package com.mytube.app.ui.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.mytube.app.ui.shell.tabContentPadding
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
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HistoryContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onOpenSettings = onOpenSettings,
        onOpenVideo = onOpenVideo,
        onRefresh = viewModel::refresh,
        onSaveVideo = viewModel::toggleSaved,
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
    onSaveVideo: (Video) -> Unit = {},
) {
    val strings = LocalStrings.current

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = tabContentPadding(),
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
                        // No "not interested" here. History is a record of what
                        // was watched, and telling the ranker off from a list of
                        // things somebody chose to watch is the wrong signal in
                        // the wrong place.
                    )
                }
            }
        }
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
                onOpenSettings = {},
                onOpenVideo = {},
                onRefresh = {},
            )
        }
    }
}
