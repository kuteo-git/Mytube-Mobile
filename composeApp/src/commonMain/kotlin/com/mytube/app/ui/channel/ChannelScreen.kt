package com.mytube.app.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.repository.SortOption
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.home.formatCount
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.tabContentPadding
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.watch.BackIcon
import com.mytube.app.ui.watch.SubscribeButton
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChannelContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenVideo = onOpenVideo,
        onRetry = viewModel::retry,
        onSelectSort = viewModel::selectSort,
        onToggleSubscribed = viewModel::toggleSubscribed,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
fun ChannelContent(
    state: ChannelState,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onRetry: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onToggleSubscribed: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val strings = LocalStrings.current

    Box(Modifier.fillMaxSize()) {
    TabScaffold(
        loading = state is ChannelState.Loading,
        needsServer = state is ChannelState.NeedsServer,
        failure = (state as? ChannelState.Failed)?.message.orEmpty(),
        noServerTitle = strings.noServerTitle,
        setTheAddress = strings.setTheAddress,
        couldNotReach = strings.couldNotReach,
        tryAgain = strings.tryAgain,
        onOpenSettings = onOpenSettings,
        onRetry = onRetry,
    ) {
        val ready = state as? ChannelState.Ready ?: return@TabScaffold
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

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = tabContentPadding(),
        ) {
            item(key = "header") {
                Header(ready.channel, ready.videoCount, mediaBaseUrl, onToggleSubscribed)
            }

            if (ready.sortOptions.isNotEmpty()) {
                item(key = "sorts") {
                    SortRow(ready.sortOptions, ready.sortToken, onSelectSort)
                }
            }

            if (ready.videos.isEmpty()) {
                item(key = "empty") {
                    EmptyState(strings.nothingHere, strings.noResultsDetail)
                }
            }

            items(ready.videos, key = { it.id }) { video ->
                VideoCard(video, mediaBaseUrl, strings, onClick = { onOpenVideo(video.id) })
            }

            if (ready.loadingMore) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(Space.lg), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    // Drawn over every state, including the failed one. This screen has no top
    // bar and no tab bar, so without it a channel that would not load is a dead
    // end — and Android's system back leaves the app entirely, while iOS has no
    // system back at all. The way out has to be on the screen.
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(top = statusBarTop(), start = Space.xs)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BackIcon, strings.back, tint = Tokens.text, modifier = Modifier.size(24.dp))
    }
    }
}

/** The status bar's height, so the arrow clears the clock. */
@Composable
private fun statusBarTop() =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * The channel, centred over its own uploads.
 *
 * No banner. The gateway sends a `bannerPath` and it is deliberately unused: a
 * banner is 200dp of decoration above the one thing the screen is for, and on a
 * phone it pushes the first video off the fold.
 */
@Composable
private fun Header(
    channel: Channel,
    videoCount: Int,
    mediaBaseUrl: String,
    onToggleSubscribed: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = imageModel(mediaBaseUrl, channel.avatarPath),
            contentDescription = channel.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(Tokens.surface),
        )
        Spacer(Modifier.height(Space.md))
        Text(
            text = channel.name,
            color = Tokens.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            // The handle first, then whichever counts the server actually sent.
            // Zeroes are left out rather than printed: a channel reading "0
            // subscribers" states a figure nobody measured.
            text = buildString {
                if (channel.handle.isNotEmpty()) append(channel.handle)
                if (channel.subscriberCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${formatCount(channel.subscriberCount, strings)} ${strings.subscribers}")
                }
                if (videoCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("$videoCount ${strings.videos}")
                }
            },
            color = Tokens.text2,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.md))
        SubscribeButton(channel.subscribed, onToggleSubscribed)
    }
}

/**
 * Latest · Popular · Oldest, as upstream named them.
 *
 * The labels are not translated. They arrive from YouTube in whatever language
 * it answered in, with an opaque token beside each; translating one would mean
 * guessing which of the three it is, and being wrong the day a fourth appears.
 */
@Composable
private fun SortRow(options: List<SortOption>, selected: String, onSelect: (SortOption) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        options.forEach { option ->
            // The default order has an empty token, so the first option is lit
            // when nothing has been chosen — otherwise the row opens with no
            // segment marked and the list looks unsorted.
            val lit = option.token == selected ||
                (selected.isEmpty() && option == options.first())
            Text(
                text = option.label,
                color = if (lit) Tokens.bg else Tokens.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (lit) Tokens.text else Tokens.surface)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

// --- previews ---------------------------------------------------------------

private fun sampleChannel(subscribed: Boolean) = Channel(
    id = "UCE_M8A5yxnLfW0KghEeajjw",
    name = "Apple",
    handle = "@Apple",
    avatarPath = "channels/apple/avatar.jpg",
    subscribed = subscribed,
    subscriberCount = 20_700_000,
)

private fun sampleReady(subscribed: Boolean) = ChannelState.Ready(
    channel = sampleChannel(subscribed),
    videoCount = 55,
    videos = listOf(
        com.mytube.app.domain.model.Video(
            id = "3uAIqqg8ZHo",
            title = "The New Mac Studio with M5 Max and M5 Ultra",
            channel = sampleChannel(subscribed),
            durationSeconds = 80,
            viewCount = 476_000,
            publishedAt = "2026-08-26T12:04:03Z",
            thumbnailPath = "https://i.ytimg.com/vi/3uAIqqg8ZHo/hq720.jpg",
        ),
    ),
    sortOptions = listOf(
        SortOption("Latest", "t-latest"),
        SortOption("Popular", "t-popular"),
        SortOption("Oldest", "t-oldest"),
    ),
    sortToken = "t-latest",
    nextPageToken = "",
)

@Composable
private fun preview(state: ChannelState) {
    MytubeTheme {
        ChannelContent(
            state = state,
            mediaBaseUrl = "",
            onBack = {},
            onOpenSettings = {},
            onOpenVideo = {},
            onRetry = {},
            onSelectSort = {},
            onToggleSubscribed = {},
            onLoadMore = {},
        )
    }
}

@Preview
@Composable
private fun ChannelPreview() = preview(sampleReady(subscribed = true))

/** Not subscribed, which is the loud state of the one button here. */
@Preview
@Composable
private fun ChannelUnsubscribedPreview() = preview(sampleReady(subscribed = false))

@Preview
@Composable
private fun ChannelVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        preview(sampleReady(subscribed = true))
    }
}
