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
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.home.formatViews
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
        onOpenVideo = onOpenVideo,
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
    onOpenVideo: (String) -> Unit,
) {
    val strings = LocalStrings.current

    Column(Modifier.fillMaxSize().background(Tokens.bg)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // The picture keeps its 16:9 box in every state. A screen whose top half
        // changes height as it loads makes everything below it jump, and the
        // title is the thing a reader is looking at while they wait.
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is WatchState.Playing -> if (player != null) {
                    VideoSurface(player, Modifier.fillMaxSize())
                    if (state.playback.isBuffering) CircularProgressIndicator()
                    PlayerControls(
                        playback = state.playback,
                        onPlayPause = onPlayPause,
                        onSeek = onSeek,
                        onSkip = onSkip,
                        onBack = onBack,
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
            is WatchState.Playing -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + Space.lg,
                ),
            ) {
                item(key = "details") {
                    Details(
                        state.video,
                        mediaBaseUrl,
                        onReact,
                        onToggleSaved,
                        onToggleSubscribed,
                    )
                }

                if (state.upNext.isNotEmpty()) {
                    item(key = "up-next") {
                        Text(
                            text = strings.upNext,
                            color = Tokens.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(
                                start = Space.lg,
                                top = Space.lg,
                                bottom = Space.sm,
                            ),
                        )
                    }
                    items(state.upNext, key = { it.id }) { next ->
                        VideoCard(next, mediaBaseUrl, strings, onClick = { onOpenVideo(next.id) })
                    }
                }
            }
        }
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
private fun Details(
    video: Video,
    mediaBaseUrl: String,
    onReact: (Reaction) -> Unit,
    onToggleSaved: () -> Unit,
    onToggleSubscribed: () -> Unit,
) {
    val strings = LocalStrings.current

    Column {
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
            Text(
                text = video.title,
                color = Tokens.text,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = formatViews(video.viewCount, strings),
                color = Tokens.text2,
                fontSize = 12.sp,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = "$mediaBaseUrl/media/${video.channel.avatarPath}",
                    contentDescription = video.channel.name,
                    contentScale = ContentScale.Crop,
                    // 40 on the watch page, against 36 in a card. From the
                    // design system, and the difference is deliberate there.
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Tokens.surface),
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    text = video.channel.name,
                    color = Tokens.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.sm))
            SubscribeButton(video.channel.subscribed, onToggleSubscribed)
        }

        Spacer(Modifier.height(Space.md))
        WatchActions(video, onReact, onToggleSaved)
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
            onOpenVideo = {},
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
