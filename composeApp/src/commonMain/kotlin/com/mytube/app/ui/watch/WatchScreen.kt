package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.home.formatCount
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.home.todayISO
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun WatchScreen(
    viewModel: WatchViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    /**
     * Open a video by *advancing* to it, which starts it at the beginning.
     *
     * Distinct from [onOpenVideo] deliberately: advancing means "play me the
     * next thing", and dropping somebody into the middle of a track they did
     * not pick reads as a glitch.
     */
    onAdvanceTo: (String) -> Unit,
    /**
     * Go back to the video watched before this one.
     *
     * The trail lives above this screen, in the app: it is a fact about the
     * sitting rather than about one video, and a ViewModel rebuilt for every
     * video could not hold it.
     */
    onPlayPrevious: () -> Unit,
    hasPrevious: Boolean,
    onOpenChannel: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WatchContent(
        state = state,
        player = viewModel.player,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onPlayPause = viewModel::playPause,
        onSeek = viewModel::seekTo,
        onSkip = viewModel::skip,
        onRetry = viewModel::retry,
        onReact = viewModel::react,
        onToggleSaved = viewModel::toggleSaved,
        onToggleSubscribed = viewModel::toggleSubscribed,
        onToggleNarration = viewModel::toggleNarration,
        onToggleAutoplay = viewModel::toggleAutoplay,
        onSelectSubtitles = viewModel::selectSubtitles,
        onPlayNext = {
            val next = (state as? WatchState.Playing)?.upNext?.firstOrNull()
            if (next != null) onAdvanceTo(next.id)
        },
        onPlayPrevious = onPlayPrevious,
        hasPrevious = hasPrevious,
        onToggleRail = viewModel::toggleRail,
        onFilterRail = viewModel::filterRail,
        onOpenVideo = onOpenVideo,
        onOpenChannel = onOpenChannel,
    )
}

@Composable
fun WatchContent(
    state: WatchState,
    player: VideoPlayer?,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
    onSkip: (Double) -> Unit,
    onRetry: () -> Unit,
    onReact: (Reaction) -> Unit,
    onToggleSaved: () -> Unit,
    onToggleSubscribed: () -> Unit,
    onToggleNarration: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onSelectSubtitles: (String) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    /** Whether anything was watched before this in the current sitting. */
    hasPrevious: Boolean,
    onToggleRail: () -> Unit,
    onFilterRail: (Boolean) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val strings = LocalStrings.current
    // Today, so the rail's "New" badge can be decided without a clock inside a
    // pure function. Read once per composition rather than per row.
    val today = remember { todayISO() }
    var fullscreen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val openShare = rememberShare()
    val share: () -> Unit = {
        val id = (state as? WatchState.Playing)?.video?.id.orEmpty()
        if (id.isNotEmpty()) openShare("https://www.youtube.com/watch?v=$id")
    }

    // Told the platform as state, not as an event: a screen disposed while
    // fullscreen must not leave the phone sideways with no system bars.
    ApplyFullscreen(fullscreen)

    Column(Modifier.fillMaxSize().background(Tokens.bg)) {
        // No status-bar gap in fullscreen — there is no status bar, and the gap
        // would be a black band where the picture should be.
        if (!fullscreen) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
        }

        // The picture keeps its 16:9 box in every state. A screen whose top half
        // changes height as it loads makes everything below it jump, and the
        // title is the thing a reader is looking at while they wait.
        Box(
            // Fullscreen drops the 16:9 box and takes the whole screen. The
            // surface letterboxes inside it, so a portrait video keeps its
            // shape rather than being stretched to the landscape frame.
            if (fullscreen) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
            },
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is WatchState.Playing -> if (player != null) {
                    VideoSurface(player, Modifier.fillMaxSize())
                    // Between the picture and the control bar: the bar covers
                    // the bottom of the frame, which is where captions go, and
                    // it wins the overlap for the few seconds it is visible.
                    SubtitleOverlay(
                        cues = state.subtitleCues,
                        positionSeconds = state.playback.positionSeconds,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    if (state.playback.isBuffering) CircularProgressIndicator()
                    PlayerControls(
                        playback = state.playback,
                        isLive = state.isLive,
                        fullscreen = fullscreen,
                        onPlayPause = onPlayPause,
                        onSeek = onSeek,
                        onSkip = onSkip,
                        // In fullscreen the back arrow leaves fullscreen rather
                        // than the video. Somebody who filled the screen wants
                        // out of *that* first, and taking the video away instead
                        // is the reading nobody intends.
                        onBack = { if (fullscreen) fullscreen = false else onBack() },
                        onToggleFullscreen = { fullscreen = !fullscreen },
                        onOpenSettings = { settingsOpen = !settingsOpen },
                        onPlayNext = onPlayNext,
                        onPlayPrevious = onPlayPrevious,
                        // The CC button is a shortcut, not a replacement for
                        // the menu: it turns the first track on and off, which
                        // is what somebody reaching for it nearly always wants.
                        // Choosing *which* track stays in the sheet.
                        onToggleSubtitles = {
                            onSelectSubtitles(
                                if (state.subtitleLanguage.isEmpty()) {
                                    state.video.subtitles.first().language
                                } else {
                                    ""
                                },
                            )
                        },
                        hasNext = state.upNext.isNotEmpty(),
                        hasPrevious = hasPrevious,
                        hasSubtitles = state.video.subtitles.isNotEmpty(),
                        subtitlesOn = state.subtitleLanguage.isNotEmpty(),
                    )
                }

                is WatchState.Loading -> CircularProgressIndicator()

                // Every other state still gets the back arrow. Without it a
                // video that will not play is a screen with no way out but the
                // system gesture — and on a members-only video that is exactly
                // where somebody lands.
                else -> BackOnly(onBack, strings.back)
            }
        }

        // Directly under the picture, so the video keeps playing above whatever
        // is being changed.
        if (state is WatchState.Playing) {
            PlayerSettingsPanel(
                visible = settingsOpen && !fullscreen,
                subtitles = state.video.subtitles,
                subtitleLanguage = state.subtitleLanguage,
                onSelectSubtitles = onSelectSubtitles,
                narrating = state.narrating,
                narration = state.narration,
                autoplay = state.autoplay,
                onToggleAutoplay = onToggleAutoplay,
                onDismiss = { settingsOpen = false },
                onToggleNarration = onToggleNarration,
            )
        }

        if (fullscreen) return@Column

        when (state) {
            is WatchState.Loading -> Unit

            is WatchState.Failed -> Message(
                title = strings.couldNotPlay,
                detail = state.message,
                actionLabel = strings.tryAgain,
                onAction = onRetry,
            )

            // Nothing is wrong and the answer changes on its own, so there is no
            // retry: saying "try again" about a broadcast that has not started
            // would be asking somebody to keep pressing a button until the world
            // catches up.
            is WatchState.Upcoming -> Message(
                title = strings.upcomingTitle,
                detail = strings.upcomingDetail,
                actionLabel = "",
                onAction = {},
            )

            // Permanent, so the reason is named and no retry is offered.
            is WatchState.Unavailable -> Message(
                title = strings.unavailableTitle,
                detail = state.reason,
                actionLabel = "",
                onAction = {},
            )

            // One scroller for everything below the picture, so the rail is
            // lazy: an up-next of twenty videos is twenty thumbnails, and
            // composing them all under a Column would fetch every one before the
            // viewer had scrolled to any.
            // One scroller for everything below the picture, in the web app's
            // order: title, channel and actions, description, comments, then
            // what plays next. It is lazy because the comments and the rail are
            // both lists of unknown length, and a Column would compose every
            // thumbnail and every comment before the viewer had scrolled to any.
            is WatchState.Playing -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + Space.lg,
                ),
            ) {
                item(key = "title") {
                    Text(
                        text = state.video.title,
                        color = Tokens.text,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = Space.lg,
                            end = Space.lg,
                            top = Space.md,
                        ),
                    )
                }

                item(key = "channel") {
                    ChannelRow(
                        video = state.video,
                        mediaBaseUrl = mediaBaseUrl,
                        onToggleSubscribed = onToggleSubscribed,
                        onOpenChannel = { onOpenChannel(state.video.channel.id) },
                    )
                }

                item(key = "actions") {
                    Spacer(Modifier.height(Space.md))
                    WatchActions(
                        video = state.video,
                        onReact = onReact,
                        onToggleSaved = onToggleSaved,
                        onShare = share,
                    )
                }

                item(key = "description") {
                    Spacer(Modifier.height(Space.md))
                    DescriptionBox(state.video, Modifier.padding(horizontal = Space.lg))
                }

                item(key = "comments") {
                    Spacer(Modifier.height(Space.xl))
                    CommentSection(state.comments.size, state.comments, state.loadingComments)
                }

                item(key = "up-next") {
                    Spacer(Modifier.height(Space.lg))
                    UpNextRail(
                        current = state.video,
                        videos = state.upNext,
                        collapsed = state.railCollapsed,
                        channelOnly = state.railChannelOnly,
                        mediaBaseUrl = mediaBaseUrl,
                        onToggleCollapsed = onToggleRail,
                        onSelectFilter = onFilterRail,
                        onOpenVideo = onOpenVideo,
                        today = today,
                    )
                }
            }
        }
    }
}

/**
 * Avatar, name, subscriber count, Subscribe.
 *
 * The subscriber line was missing and it is not decoration: without it the row
 * is a name and a button, and the button is the only thing with any weight — so
 * the eye goes to Subscribe rather than to whose channel this is.
 */
@Composable
private fun ChannelRow(
    video: Video,
    mediaBaseUrl: String,
    onToggleSubscribed: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = imageModel(mediaBaseUrl, video.channel.avatarPath),
            contentDescription = video.channel.name,
            contentScale = ContentScale.Crop,
            // 40 on the watch page, against 36 in a card and 24 on a comment.
            // From the design system, and the differences are deliberate there.
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Tokens.surface)
                .clickable(onClick = onOpenChannel),
        )
        Spacer(Modifier.width(Space.md))
        // The name opens the channel too, which is what the web does and what a
        // thumb aiming at a 40dp circle needs — the avatar alone is a small
        // target for the one thing on this row that is not a button.
        Column(Modifier.weight(1f).clickable(onClick = onOpenChannel)) {
            Text(
                text = video.channel.name,
                color = Tokens.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.channel.subscriberCount > 0) {
                Text(
                    text = formatCount(video.channel.subscriberCount, strings) + " " +
                        strings.subscribersShort,
                    color = Tokens.text2,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.width(Space.sm))
        SubscribeButton(video.channel.subscribed, onToggleSubscribed)
    }
}

@Composable
private fun BackOnly(onBack: () -> Unit, label: String) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(Space.sm)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = BackIcon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun Message(title: String, detail: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Tokens.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(Space.xs))
            Text(detail, color = Tokens.text2, fontSize = 12.sp)
        }
        if (actionLabel.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// --- previews ---------------------------------------------------------------
//
// `player = null` throughout: a Preview has no decoder, and the surface is the
// one part of this screen that cannot be drawn without a device. Everything the
// previews exist to check — the controls, the pills, the subscribe state, the
// rail — sits outside it.

private fun sample(id: String, title: String, subscribed: Boolean = false) = Video(
    id = id,
    title = title,
    channel = Channel(
        id = "c",
        name = "CBC News",
        handle = "@CBCNews",
        avatarPath = "channels/c/avatar.jpg",
        subscribed = subscribed,
    ),
    durationSeconds = 1397,
    viewCount = 78_200,
    publishedAt = "2026-08-28T05:35:52Z",
    thumbnailPath = "thumbnails/$id.jpg",
)

private fun playing(video: Video, upNext: List<Video> = emptyList()) = WatchState.Playing(
    video = video,
    playback = PlaybackState(isPlaying = true, positionSeconds = 312.0, durationSeconds = 1397.0),
    upNext = upNext,
)

@Composable
private fun preview(state: WatchState) {
    MytubeTheme {
        WatchContent(
            state = state,
            player = null,
            mediaBaseUrl = "",
            onBack = {},
            onPlayPause = {},
            onSeek = {},
            onSkip = {},
            onRetry = {},
            onReact = {},
            onToggleSaved = {},
            onToggleSubscribed = {},
            onToggleNarration = {},
            onToggleAutoplay = {},
            onSelectSubtitles = {},
            onPlayNext = {},
            onPlayPrevious = {},
            hasPrevious = false,
            onToggleRail = {},
            onFilterRail = {},
            onOpenVideo = {},
            onOpenChannel = {},
        )
    }
}

@Preview
@Composable
private fun WatchPreview() {
    preview(
        playing(
            sample("a", "Has the Carney government lost trust in the Trump administration?"),
            upNext = listOf(sample("b", "The Chair of Theseus"), sample("c", "One Day Builds")),
        ),
    )
}

/** Liked, saved and subscribed — every control in its lit state at once. */
@Preview
@Composable
private fun WatchEngagedPreview() {
    preview(
        playing(
            sample("a", "Has the Carney government lost trust?", subscribed = true)
                .copy(reaction = Reaction.Like, saved = true),
        ),
    )
}

@Preview
@Composable
private fun WatchUnavailablePreview() {
    preview(WatchState.Unavailable(sample("a", "Members only"), "members_only"))
}

@Preview
@Composable
private fun WatchVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        preview(playing(sample("a", "Đánh giá Nothing Phone (4a) Pro")))
    }
}
