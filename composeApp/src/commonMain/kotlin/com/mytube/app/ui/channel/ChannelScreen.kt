package com.mytube.app.ui.channel

import com.mytube.app.ui.home.videoMenu
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
import com.mytube.app.ui.tv.fullWidthItem
import com.mytube.app.ui.tv.headingItem
import com.mytube.app.ui.tv.TvGridCard
import com.mytube.app.ui.tv.TvGrid
import com.mytube.app.ui.tv.TvGridSkeleton
import com.mytube.app.ui.shell.LocalTelevision
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.ChannelPlaylist
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.SortOption
import com.mytube.app.ui.playlist.PlaylistRow
import com.mytube.app.ui.playlist.PlaylistIcon
import com.mytube.app.ui.watch.QueueItem
import com.mytube.app.ui.watch.asQueueItem
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.ChannelAvatar
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.home.formatCount
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.shell.CollapsingTabPager
import com.mytube.app.ui.shell.rememberTabPagerState
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.FeedSkeleton
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.collapsingListPadding
import com.mytube.app.ui.shell.detailTopChrome
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.shell.glassControl
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.watch.SubscribeButton
import com.mytube.app.ui.shell.pressableGlassControl
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
    onOpenVideo: (String, List<QueueItem>) -> Unit,
    /** Opens the sheet asking which collections this belongs in. See `App.kt`. */
    onSaveToPlaylist: (Video) -> Unit,
    /** Open one of the channel's playlists: its id and its title for the header. */
    onOpenPlaylist: (ChannelPlaylist) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    ChannelContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        opening = opening,
        openFailed = openFailed,
        tab = tab,
        playlistsState = playlists,
        // The row is written into the catalogue first when it has no entry — see
        // [ChannelViewModel.openVideo]. The screen only says where to go next.
        onOpenVideo = { video, queue -> viewModel.openVideo(video, queue, onOpenVideo) },
        onRetry = viewModel::retry,
        onSelectSort = viewModel::selectSort,
        onToggleSubscribed = viewModel::toggleSubscribed,
        onLoadMore = viewModel::loadMore,
        onSaveVideo = onSaveToPlaylist,
        onSelectTab = viewModel::selectTab,
        onOpenPlaylist = onOpenPlaylist,
        onLoadMorePlaylists = viewModel::loadMorePlaylists,
        onRetryPlaylists = viewModel::retryPlaylists,
    )
}

@Composable
fun ChannelContent(
    state: ChannelState,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    /** The id of the row being written, so its card can say so and refuse a second tap. */
    opening: String = "",
    /** The last attempt came back with no id. */
    openFailed: Boolean = false,
    tab: ChannelTab = ChannelTab.Videos,
    playlistsState: PlaylistsState = PlaylistsState.Idle,
    onOpenVideo: (Video, List<QueueItem>) -> Unit,
    onRetry: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onToggleSubscribed: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveVideo: (Video) -> Unit = {},
    onSelectTab: (ChannelTab) -> Unit = {},
    onOpenPlaylist: (ChannelPlaylist) -> Unit = {},
    onLoadMorePlaylists: () -> Unit = {},
    onRetryPlaylists: () -> Unit = {},
) {
    val strings = LocalStrings.current

    // Recorded, so the miniplayer floating over this page is glass here too.
    Box(Modifier.fillMaxSize().glassSource()) {
    if (state is ChannelState.Missing) {
        EmptyState(strings.channelMissing, strings.channelMissingDetail)
    } else TabScaffold(
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

        // The ids in the order shown, computed once per page rather than per
        // card: it is the same list for every row, and rebuilding it inside
        // `items` would allocate one per visible thumbnail on every scroll frame.
        val queue = remember(ready.videos) { ready.videos.map { it.asQueueItem() } }

        // A grid of Home's cards on a television, as the other pages of
        // videos are — see `TvGrid`. The same header, sorts, skeleton and
        // next-page rule; the cards are what change.
        //
        // No Playlists tab on the television: browsing a channel's playlists is
        // a phone act, and a focusable tab row in the grid's heading is a focus
        // problem of its own, left for when the set needs it.
        if (LocalTelevision.current) {
            val gridState = rememberLazyGridState()
            val gridAtEnd by remember {
                derivedStateOf {
                    val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= gridState.layoutInfo.totalItemsCount - 4
                }
            }
            LaunchedEffect(gridState) {
                snapshotFlow { gridAtEnd }.collect { if (it) onLoadMore() }
            }
            TvGrid(Modifier.fillMaxSize(), state = gridState) {
                // One heading, sorts and all: focus on a sort chip brings the
                // avatar back too — see `headingItem`.
                headingItem(key = "header") {
                    Header(ready.channel, ready.videoCount, mediaBaseUrl, onToggleSubscribed)
                    if (ready.sortOptions.isNotEmpty()) {
                        SortRow(ready.sortOptions, ready.sortIndex, onSelectSort)
                    }
                }
                if (ready.sorting) {
                    fullWidthItem(key = "sorting") { TvGridSkeleton(rows = 1) }
                }
                if (ready.videos.isEmpty() && !ready.sorting) {
                    fullWidthItem(key = "empty") {
                        EmptyState(strings.nothingHere, strings.noResultsDetail)
                    }
                }
                if (openFailed) {
                    fullWidthItem(key = "open-failed") {
                        Text(
                            text = strings.youtubeUnreachable,
                            color = Tokens.text2,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                        )
                    }
                }
                items(if (ready.sorting) emptyList() else ready.videos, key = { it.id }) { video ->
                    TvGridCard(
                        video = video,
                        mediaBaseUrl = mediaBaseUrl,
                        strings = strings,
                        onClick = { onOpenVideo(video, queue) },
                        opening = opening == video.id,
                        menu = remember(video, strings) { videoMenu(strings, onSave = { onSaveVideo(video) }) },
                    )
                }
                if (ready.loadingMore) {
                    fullWidthItem(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(Space.lg), Alignment.Center) {
                            AdaptiveCircularProgressIndicator()
                        }
                    }
                }
            }
            return@TabScaffold
        }

        // The phone draws the header, then a two-tab strip over a pager the two
        // lists swipe between. The header collapses as the list scrolls and the
        // strip pins under the top chrome — see `CollapsingTabPager`; each list
        // keeps its own scroll underneath.
        val pager = rememberTabPagerState(
            pageCount = ChannelTab.entries.size,
            initialPage = tab.ordinal,
        )
        // The pager is the truth about which tab shows; the view model is told so
        // it can load the playlists the first time that page is reached.
        LaunchedEffect(pager) {
            snapshotFlow { pager.currentPage }.collect { onSelectTab(ChannelTab.entries[it]) }
        }

        CollapsingTabPager(
            tabs = listOf(strings.videosTab, strings.playlists),
            state = pager,
            topInset = detailTopChrome(),
            header = {
                Header(ready.channel, ready.videoCount, mediaBaseUrl, onToggleSubscribed)
            },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                ChannelTab.Videos.ordinal -> ChannelVideosList(
                    ready = ready,
                    queue = queue,
                    mediaBaseUrl = mediaBaseUrl,
                    opening = opening,
                    openFailed = openFailed,
                    onOpenVideo = onOpenVideo,
                    onSelectSort = onSelectSort,
                    onLoadMore = onLoadMore,
                    onSaveVideo = onSaveVideo,
                )
                else -> ChannelPlaylistsList(
                    playlistsState = playlistsState,
                    mediaBaseUrl = mediaBaseUrl,
                    onOpenPlaylist = onOpenPlaylist,
                    onLoadMorePlaylists = onLoadMorePlaylists,
                    onRetryPlaylists = onRetryPlaylists,
                )
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
 * The videos page of the channel pager: the sorts, then the uploads.
 *
 * The header and the tab strip sit above the pager, so this is only the list —
 * its own scroll state, so swiping back to it lands where it was left.
 */
@Composable
private fun ChannelVideosList(
    ready: ChannelState.Ready,
    queue: List<QueueItem>,
    mediaBaseUrl: String,
    opening: String,
    openFailed: Boolean,
    onOpenVideo: (Video, List<QueueItem>) -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onLoadMore: () -> Unit,
    onSaveVideo: (Video) -> Unit,
) {
    val strings = LocalStrings.current
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
        // The top inset clears the collapsing header and the tab strip and
        // shrinks as they collapse, so the feed rises with them; the bottom
        // clears the miniplayer. See `CollapsingTabPager`.
        contentPadding = collapsingListPadding(),
    ) {
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

        // A press that could not be answered says so, above the list it was
        // made in. Silence here is a dead button: the card stops spinning
        // and nothing else happens, which reads as the app having lost the
        // tap rather than as YouTube having refused it.
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

        items(if (ready.sorting) emptyList() else ready.videos, key = { it.id }) { video ->
            VideoCard(
                video = video,
                mediaBaseUrl = mediaBaseUrl,
                strings = strings,
                onClick = { onOpenVideo(video, queue) },
                onSave = { onSaveVideo(video) },
                // Fetching its metadata. The card says so over its own
                // picture and stops taking presses, because writing the row
                // is a round trip to YouTube and a card that looks idle is
                // one somebody presses again.
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

/**
 * The playlists page of the channel pager: the lists the channel published.
 * Loads lazily on first open; a failure here leaves the videos page untouched
 * and offers its own retry.
 */
@Composable
private fun ChannelPlaylistsList(
    playlistsState: PlaylistsState,
    mediaBaseUrl: String,
    onOpenPlaylist: (ChannelPlaylist) -> Unit,
    onLoadMorePlaylists: () -> Unit,
    onRetryPlaylists: () -> Unit,
) {
    val strings = LocalStrings.current
    val listState = rememberLazyListState()
    val readyPlaylists = playlistsState as? PlaylistsState.Ready
    val atEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { atEnd }.collect { if (it) onLoadMorePlaylists() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // The collapsing top inset and the miniplayer bottom, as the videos list
        // above. See `CollapsingTabPager`.
        contentPadding = collapsingListPadding(),
    ) {
        when (playlistsState) {
            is PlaylistsState.Idle, is PlaylistsState.Loading ->
                item(key = "loading") { FeedSkeleton(cards = 3) }

            is PlaylistsState.Failed ->
                item(key = "failed") {
                    Column(
                        Modifier.fillMaxWidth().padding(Space.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            strings.couldNotReach,
                            color = Tokens.text2,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Space.md))
                        Text(
                            text = strings.tryAgain,
                            color = Tokens.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .pressableGlassControl(RoundedCornerShape(percent = 50)) { onRetryPlaylists() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

            is PlaylistsState.Ready ->
                if (playlistsState.playlists.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(strings.noPlaylistsHere, strings.noPlaylistsHereDetail)
                    }
                } else {
                    items(playlistsState.playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            title = playlist.title,
                            detail = if (playlist.hasCount) strings.playlistCount(playlist.videoCount) else "",
                            thumbnailPath = playlist.thumbnailUrl,
                            mediaBaseUrl = mediaBaseUrl,
                            onClick = { onOpenPlaylist(playlist) },
                            icon = PlaylistIcon,
                        )
                    }
                }
        }

        if (readyPlaylists?.loadingMore == true) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(Space.lg), Alignment.Center) {
                    AdaptiveCircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * The channel, centred over its own uploads.
 *
 * **No banner**, and this is the second time that has been decided.
 *
 * It was left out first because a banner drawn YouTube's way is 200dp of
 * decoration that pushes the first video off the fold. Then it was drawn as a
 * blurred, tinted ground behind the header, costing no height — which answered
 * the objection and produced a different one: a channel's banner is somebody
 * else's composition, and 24dp of blur over a page's own colour makes a smear
 * whose only job is to sit behind a name that was perfectly legible without it.
 * Reported plainly, and it was right.
 *
 * `bannerPath` stays on the DTO and on the domain type. It is what the gateway
 * sends and the mapper's job is to carry it; the decision not to draw it belongs
 * here, on the screen that would.
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
                    .pressableGlassControl(
                        RoundedCornerShape(percent = 50),
                        selected = lit,
                    ) { onSelect(option) }
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

private val samplePlaylists = PlaylistsState.Ready(
    playlists = listOf(
        ChannelPlaylist(
            id = "PL1",
            title = "Every Apple Event, in order",
            thumbnailUrl = "https://i.ytimg.com/vi/3uAIqqg8ZHo/hq720.jpg",
            videoCount = 42,
        ),
        ChannelPlaylist(
            id = "PL2",
            title = "Shorts",
            thumbnailUrl = "",
            videoCount = 0,
        ),
    ),
    nextPageToken = "",
)

@Composable
private fun preview(
    state: ChannelState,
    tab: ChannelTab = ChannelTab.Videos,
    playlistsState: PlaylistsState = PlaylistsState.Idle,
) {
    MytubeTheme {
        ChannelContent(
            state = state,
            mediaBaseUrl = "",
            onBack = {},
            onOpenSettings = {},
            tab = tab,
            playlistsState = playlistsState,
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

/** The Playlists tab with a few lists. */
@Preview
@Composable
private fun ChannelPlaylistsPreview() =
    preview(sampleReady(subscribed = true), ChannelTab.Playlists, samplePlaylists)

/** The Playlists tab for a channel that has published none. */
@Preview
@Composable
private fun ChannelPlaylistsEmptyPreview() =
    preview(sampleReady(subscribed = true), ChannelTab.Playlists, PlaylistsState.Ready(emptyList(), ""))

/** The Playlists tab still loading. */
@Preview
@Composable
private fun ChannelPlaylistsLoadingPreview() =
    preview(sampleReady(subscribed = true), ChannelTab.Playlists, PlaylistsState.Loading)

/** Not subscribed, which is the loud state of the one button here. */
@Preview
@Composable
private fun ChannelUnsubscribedPreview() = preview(sampleReady(subscribed = false))

/** A channel YouTube says does not exist. */
@Preview
@Composable
private fun ChannelMissingPreview() = preview(ChannelState.Missing)

@Preview
@Composable
private fun ChannelVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        preview(sampleReady(subscribed = true))
    }
}

/** The Playlists tab in Vietnamese, labels and counts and all. */
@Preview
@Composable
private fun ChannelPlaylistsVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        preview(sampleReady(subscribed = true), ChannelTab.Playlists, samplePlaylists)
    }
}
