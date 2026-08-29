package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.home.formatViews
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.theme.Tokens

@Composable
fun WatchScreen(
    viewModel: WatchViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WatchContent(
        state = state,
        player = viewModel.player,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onPlayPause = viewModel::playPause,
        onSeek = viewModel::seekTo,
        onRetry = viewModel::retry,
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
    onRetry: () -> Unit,
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
                    // The whole picture is the play/pause target. A phone has no
                    // pointer to reveal controls with, and a tap on the video is
                    // what every player on the device does.
                    Box(Modifier.fillMaxSize().clickable(onClick = onPlayPause))
                }

                is WatchState.Loading -> CircularProgressIndicator()

                else -> Unit
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

            is WatchState.Playing -> Column {
                Progress(state.playback, onSeek)
                Details(state, mediaBaseUrl)
            }
        }
    }
}

@Composable
private fun Progress(playback: PlaybackState, onSeek: (Double) -> Unit) {
    Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Tokens.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(playback.progress)
                    .fillMaxHeight()
                    .background(Tokens.brand),
            )
        }
        Spacer(Modifier.height(Space.xs))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(playback.positionSeconds.toInt()),
                color = Tokens.text2,
                fontSize = 12.sp,
            )
            Text(
                formatDuration(playback.durationSeconds.toInt()),
                color = Tokens.text2,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Details(state: WatchState.Playing, mediaBaseUrl: String) {
    val strings = LocalStrings.current
    val video = state.video

    Column(Modifier.padding(Space.lg)) {
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

        Spacer(Modifier.height(Space.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = "$mediaBaseUrl/media/${video.channel.avatarPath}",
                contentDescription = video.channel.name,
                contentScale = ContentScale.Crop,
                // 40 on the watch page, against 36 in a card. From the design
                // system, and the difference is deliberate there.
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Tokens.surface),
            )
            Spacer(Modifier.width(Space.md))
            Text(
                text = video.channel.name,
                color = Tokens.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
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
