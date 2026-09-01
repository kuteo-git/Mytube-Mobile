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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.SortOption
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.ChannelAvatar
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.home.formatCount
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.FeedSkeleton
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.detailContentPadding
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.shell.glassControl
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.watch.SubscribeButton
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    /**
     * Open a video, and hand over the list it came from.
     *
     * The second argument is what makes this a playlist rather than a page of
     * links: the ids in the order the page is showing them, so pressing next
     * inside the video stays in *this* channel in *this* order. The web app
     * carries the same thing in the URL as `?list=channel:…&sort=…`, and the
     * reasoning it wrote down applies here — a sorted list you can only leave by
     * playing something is not a sorted list, it is a way of finding one video.
     */
    onOpenVideo: (String, List<String>) -> Unit,
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
        onSaveVideo = viewModel::toggleSaved,
    )
}

@Composable
fun ChannelContent(
    state: ChannelState,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String, List<String>) -> Unit,
    onRetry: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onToggleSubscribed: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveVideo: (Video) -> Unit = {},
) {
    val strings = LocalStrings.current

    // Recorded, so the miniplayer floating over this page is glass here too.
    Box(Modifier.fillMaxSize().glassSource()) {
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

        // The ids in the order shown, computed once per page rather than per
        // card: it is the same list for every row, and rebuilding it inside
        // `items` would allocate one per visible thumbnail on every scroll frame.
        val queue = remember(ready.videos) { ready.videos.map { it.id } }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                ,
            contentPadding = detailContentPadding(),
        ) {
            item(key = "header") {
                Header(ready.channel, ready.videoCount, mediaBaseUrl, onToggleSubscribed)
            }

            if (ready.sortOptions.isNotEmpty()) {
                item(key = "sorts") {
                    SortRow(ready.sortOptions, ready.sortIndex, onSelectSort)
                }
            }

            // While a different order is on its way the *list* is a skeleton and
            // the header is not.
            //
            // The page used to dim to 0.45 whole, which says "something is
            // happening" and not *what* — and it left the old order legible
            // underneath, so the first thing to change when the answer arrived
            // was a list somebody was already reading. The header, the avatar
            // and the sort row are not being reloaded, so they stay solid; the
            // videos are, and they are drawn as what is coming.
            if (ready.sorting) {
                // No top padding of its own: it stands exactly where the first
                // card stands, so the page does not jump by a gap's height when
                // the answer lands.
                item(key = "sorting") { FeedSkeleton(cards = 3) }
            }

            if (ready.videos.isEmpty() && !ready.sorting) {
                item(key = "empty") {
                    EmptyState(strings.nothingHere, strings.noResultsDetail)
                }
            }

            items(if (ready.sorting) emptyList() else ready.videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    mediaBaseUrl = mediaBaseUrl,
                    strings = strings,
                    onClick = { onOpenVideo(video.id, queue) },
                    onSave = { onSaveVideo(video) },
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

    // Drawn over every state, including the failed one. This screen has no top
    // bar and no tab bar, so without it a channel that would not load is a dead
    // end — and Android's system back leaves the app entirely, while iOS has no
    // system back at all. The way out has to be on the screen.
    DetailBack(onBack, strings.back)
    }
}


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

    Box(Modifier.fillMaxWidth()) {
        // The banner, behind everything above the Subscribe button.
        //
        // It was left out on the reasoning that a banner is 200dp of decoration
        // above the one thing the screen is for. That was right about a banner
        // drawn the way YouTube draws one — a band of its own that pushes the
        // first video off the fold — and it is not what this is: the picture
        // fills the space the header already occupies, cropped from its centre,
        // with the name and the counts sitting on it. It costs no height at all.
        //
        // Blurred, and that is what makes it usable as a ground: a channel's
        // banner is somebody else's composition, with its own text and its own
        // focal point, and reading a name over it needs the picture to stop
        // being a picture.
        if (channel.bannerPath.isNotEmpty()) {
            AsyncImage(
                model = imageModel(mediaBaseUrl, channel.bannerPath),
                contentDescription = null,
                // Filled and centre-cropped: a 6:1 banner in a 4:3 box either
                // crops or letterboxes, and a letterbox here would be two black
                // bands around somebody's artwork.
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(BANNER_BLUR)
                    // Dimmed after the blur, not before: the tint is what keeps
                    // white text legible over a pale banner, and blurring a
                    // tinted image just makes a pale one paler.
                    .drawWithContent {
                        drawContent()
                        drawRect(Tokens.bg.copy(alpha = BANNER_TINT))
                    },
            )
        }

    Column(
        // No bottom padding: the sort row below owns the gap on both of its
        // sides. Two paddings meeting in the middle is how the row ended up with
        // 24dp above it and 8 below — reported as the cluster sitting high.
        Modifier
            .fillMaxWidth()
            .padding(start = Space.lg, end = Space.lg, top = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelAvatar(
            name = channel.name,
            mediaBaseUrl = mediaBaseUrl,
            path = channel.avatarPath,
            size = 72.dp,
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
        // The banner stops here — under the counts, with a gap — which is what
        // was asked for and is also where it stops being useful: below this the
        // page is a list, and a list needs a plain ground.
        Spacer(Modifier.height(Space.md))
        SubscribeButton(channel.subscribed, onToggleSubscribed)
    }
    }
}

/** How far the banner is pushed out of focus so a name can sit on it. */
private val BANNER_BLUR = 24.dp

/** And how much of the page's own colour is held over it. */
private const val BANNER_TINT = 0.45f

/**
 * Latest · Popular · Oldest, as upstream named them.
 *
 * The labels are not translated. They arrive from YouTube in whatever language
 * it answered in, with an opaque token beside each; translating one would mean
 * guessing which of the three it is, and being wrong the day a fourth appears.
 */
@Composable
private fun SortRow(options: List<SortOption>, selected: Int, onSelect: (SortOption) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            // Equal above and below, and the header contributes neither.
            .padding(horizontal = Space.lg, vertical = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        options.forEachIndexed { index, option ->
            // Lit by **position**, not by token.
            //
            // Every answer carries a fresh set of tokens, so the one that was
            // sent matches none of the ones that come back — the row ended up
            // with nothing lit the moment a sort landed, which is exactly what
            // "pressing Popular does nothing" looks like. The order is the
            // server's own and is stable within a channel.
            val lit = index == selected
            Text(
                text = option.label,
                color = if (lit) Tokens.bg else Tokens.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .glassControl(RoundedCornerShape(percent = 50), selected = lit)
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
            onOpenVideo = { _, _ -> },
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
