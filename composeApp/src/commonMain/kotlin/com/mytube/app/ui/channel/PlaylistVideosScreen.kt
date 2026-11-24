package com.mytube.app.ui.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.detailContentPadding
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.watch.QueueItem
import com.mytube.app.ui.watch.asQueueItem
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One of a channel's playlists, opened from its Playlists tab.
 *
 * A phone page: it is reached only from that tab, which the television does not
 * draw. The header is the playlist's title; the list is its videos, each opened
 * with the whole playlist as its up-next, so next stays inside the list in its
 * order — the same promise the channel page makes about its uploads.
 */
@Composable
fun PlaylistVideosScreen(
    viewModel: PlaylistVideosViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String, List<QueueItem>) -> Unit,
    onSaveToPlaylist: (Video) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()

    PlaylistVideosContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        opening = opening,
        openFailed = openFailed,
        onOpenVideo = { video, queue -> viewModel.openVideo(video, queue, onOpenVideo) },
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onSaveVideo = onSaveToPlaylist,
    )
}

@Composable
fun PlaylistVideosContent(
    state: PlaylistVideosState,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    opening: String = "",
    openFailed: Boolean = false,
    onOpenVideo: (Video, List<QueueItem>) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveVideo: (Video) -> Unit = {},
) {
    val strings = LocalStrings.current

    // Recorded, so the miniplayer floating over this page is glass here too.
    Box(Modifier.fillMaxSize().glassSource()) {
        TabScaffold(
            loading = state is PlaylistVideosState.Loading,
            needsServer = state is PlaylistVideosState.NeedsServer,
            failure = (state as? PlaylistVideosState.Failed)?.message.orEmpty(),
            noServerTitle = strings.noServerTitle,
            setTheAddress = strings.setTheAddress,
            couldNotReach = strings.couldNotReach,
            tryAgain = strings.tryAgain,
            onOpenSettings = onOpenSettings,
            onRetry = onRetry,
        ) {
            val ready = state as? PlaylistVideosState.Ready ?: return@TabScaffold
            val listState = rememberLazyListState()
            val atEnd by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= ready.videos.lastIndex - 3
                }
            }
            LaunchedEffect(listState) {
                snapshotFlow { atEnd }.collect { if (it) onLoadMore() }
            }

            val queue = remember(ready.videos) { ready.videos.map { it.asQueueItem() } }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = detailContentPadding(),
            ) {
                item(key = "header") {
                    Text(
                        text = ready.title,
                        color = Tokens.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = Space.lg, vertical = Space.lg),
                    )
                }

                if (ready.videos.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(strings.emptyPlaylist, strings.emptyPlaylistDetail)
                    }
                }

                if (openFailed) {
                    item(key = "open-failed") {
                        Text(
                            text = strings.youtubeUnreachable,
                            color = Tokens.text2,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                        )
                    }
                }

                items(ready.videos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        mediaBaseUrl = mediaBaseUrl,
                        strings = strings,
                        onClick = { onOpenVideo(video, queue) },
                        onSave = { onSaveVideo(video) },
                        opening = opening == video.id,
                    )
                }

                if (ready.loadingMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(Space.lg), Alignment.Center) {
                            AdaptiveCircularProgressIndicator()
                        }
                    }
                }
            }
        }

        // The only way out: this page has no top bar and no tab bar, the same as
        // the channel page it opened from.
        DetailBack(onBack, strings.back)
    }
}

// --- previews ---------------------------------------------------------------

private fun sampleReady() = PlaylistVideosState.Ready(
    title = "Every Apple Event, in order",
    videos = listOf(
        Video(
            id = "3uAIqqg8ZHo",
            title = "The New Mac Studio with M5 Max and M5 Ultra",
            channel = com.mytube.app.domain.model.Channel(
                id = "", name = "Marques Brownlee", handle = "", avatarPath = "",
            ),
            durationSeconds = 80,
            viewCount = 476_000,
            publishedAt = "2026-08-26T12:04:03Z",
            thumbnailPath = "https://i.ytimg.com/vi/3uAIqqg8ZHo/hq720.jpg",
        ),
    ),
    nextPageToken = "",
)

@Composable
private fun preview(state: PlaylistVideosState) {
    MytubeTheme {
        PlaylistVideosContent(
            state = state,
            mediaBaseUrl = "",
            onBack = {},
            onOpenSettings = {},
            onOpenVideo = { _, _ -> },
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview
@Composable
private fun PlaylistVideosPreview() = preview(sampleReady())

@Preview
@Composable
private fun PlaylistVideosEmptyPreview() =
    preview(PlaylistVideosState.Ready("Shorts", emptyList(), ""))

@Preview
@Composable
private fun PlaylistVideosLoadingPreview() = preview(PlaylistVideosState.Loading)

@Preview
@Composable
private fun PlaylistVideosVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        preview(sampleReady())
    }
}
