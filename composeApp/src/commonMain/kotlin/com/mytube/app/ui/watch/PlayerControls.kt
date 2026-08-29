package com.mytube.app.ui.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.theme.Tokens

/**
 * How long the controls stay up after a touch, in milliseconds.
 *
 * Five seconds, from the server charter: *"Controls hide after 3s (mouse) / 5s
 * (finger)."* A finger gets longer because there is no pointer to re-summon them
 * with — on a mouse the controls come back the moment it moves, and on a phone
 * they come back only when somebody taps, which costs a tap.
 */
private const val HIDE_AFTER_MILLIS = 5_000L

/** How far the skip buttons jump. The number every player on the device uses. */
const val SKIP_SECONDS = 10.0

/**
 * The controls over the picture.
 *
 * ## Why a tap shows them rather than playing or pausing
 *
 * The charter draws the line: *"Mouse click = play/pause; touch tap = show/hide
 * controls."* A finger has no hover, so the tap has to do the job hovering does
 * on a desktop — and a phone where tapping the picture pauses is a phone where
 * every attempt to see how far through you are stops the video.
 *
 * ## Why the controls are drawn over the video rather than under it
 *
 * They were under it, as a static bar, and that is a different thing: a readout.
 * A readout cannot be dragged, so there was no way to move within a video except
 * to restart it. The bar here is the same shape but it takes a finger.
 */
@Composable
fun PlayerControls(
    playback: PlaybackState,
    /**
     * A broadcast on air.
     *
     * It has no length, so there is nothing to divide by and nothing honest to
     * draw in a bar. The charter records what happens when one is drawn anyway:
     * `position / duration` on a stream 26 minutes into a live video is
     * **155,700%** — a bar painted solid red from the first second, reading
     * "25:57 / 0:00" beside it.
     */
    isLive: Boolean,
    fullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
    onSkip: (Double) -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayNext: () -> Unit,
    hasNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var visible by remember { mutableStateOf(true) }
    // Bumped on every interaction. The timer keys on it, so touching anything
    // restarts the countdown rather than letting the controls vanish under a
    // finger that is still using them.
    var lastTouch by remember { mutableStateOf(0) }

    LaunchedEffect(visible, lastTouch, playback.isPlaying) {
        // A paused video keeps its controls. Hiding them leaves a still frame
        // with no sign the app is even running, and the one thing somebody
        // paused for is usually the button to start again.
        if (!visible || !playback.isPlaying) return@LaunchedEffect
        kotlinx.coroutines.delay(HIDE_AFTER_MILLIS)
        visible = false
    }

    Box(
        modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                visible = !visible
                lastTouch++
            },
    ) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            // A scrim, not a solid. White glyphs over a bright frame are
            // unreadable, and darkening the whole picture to fix that is
            // punishing the video for the controls.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {

                ControlButton(
                    icon = BackIcon,
                    label = strings.back,
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(Space.sm),
                )

                // The bar the web app draws: a thin progress line across the
                // whole width, and one row under it — play, next, the clock on
                // the left; audio, settings, fullscreen on the right.
                //
                // It replaced a centre play button flanked by two ±10s circles.
                // That arrangement is what a phone's *system* player uses, and
                // it puts the three most-pressed controls over the middle of the
                // picture — exactly where somebody is looking. Compared with the
                // web app on a phone, it was the largest single difference on
                // the screen.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    if (!isLive) {
                        SeekBar(
                            progress = playback.progress,
                            enabled = playback.durationSeconds > 0,
                            onSeekFraction = {
                                onSeek(it * playback.durationSeconds)
                                lastTouch++
                            },
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.sm, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(
                            icon = if (playback.isPlaying) PauseIcon else PlayIcon,
                            label = if (playback.isPlaying) strings.pause else strings.play,
                            onClick = { onPlayPause(); lastTouch++ },
                        )
                        // The next video, which is the first row of the rail
                        // below — so the button and the list always name the
                        // same thing. Absent when the rail is empty, rather
                        // than drawn and dead.
                        if (hasNext) {
                            ControlButton(
                                icon = NextIcon,
                                label = strings.playNext,
                                onClick = { onPlayNext(); lastTouch++ },
                            )
                        }

                        Spacer(Modifier.width(Space.xs))
                        if (isLive) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape)
                                        .background(Tokens.brand),
                                )
                                Spacer(Modifier.width(Space.sm))
                                Text(
                                    text = strings.live,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        } else {
                            Text(
                                text = formatDuration(playback.positionSeconds.toInt()) +
                                    " / " + formatDuration(playback.durationSeconds.toInt()),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        ControlButton(
                            icon = SettingsGearIcon,
                            label = strings.settingsInPlayer,
                            onClick = { onOpenSettings(); lastTouch++ },
                        )
                        ControlButton(
                            icon = if (fullscreen) ShrinkIcon else ExpandIcon,
                            label = if (fullscreen) strings.exitFullscreen
                            else strings.fullscreen,
                            onClick = { onToggleFullscreen(); lastTouch++ },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bar, and the only thing here that takes a drag.
 *
 * Disabled until the duration is known. A bar that can be dragged before the
 * player knows how long the video is computes a target from zero, which is a
 * seek to zero — the video restarting is what a viewer sees, and nothing
 * explains it.
 */
@Composable
private fun SeekBar(
    progress: Float,
    enabled: Boolean,
    onSeekFraction: (Double) -> Unit,
) {
    var width by remember { mutableStateOf(1f) }
    // What the finger is on while it is down. The player's own progress keeps
    // arriving during a drag and would fight it — the thumb would jump back to
    // wherever playback is between frames.
    var dragging by remember { mutableStateOf(-1f) }
    val shown = if (dragging >= 0f) dragging else progress

    Box(
        Modifier
            .fillMaxWidth()
            // A 3dp line is what the design system draws, and 3dp is nothing to
            // aim at. The touch target is 24dp and the line is centred in it.
            .height(24.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            // Tapping the bar moves there. Its own pointerInput rather than a
            // branch inside the drag detector: the two gestures are recognised
            // separately, and combining them means a tap has to be re-derived
            // from a drag that never passed the slop threshold.
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeekFraction((offset.x / width).coerceIn(0f, 1f).toDouble())
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = (it.x / width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        if (dragging >= 0f) onSeekFraction(dragging.toDouble())
                        dragging = -1f
                    },
                    onDragCancel = { dragging = -1f },
                    onHorizontalDrag = { change, _ ->
                        dragging = (change.position.x / width).coerceIn(0f, 1f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(Tokens.brand))
        }
        // The thumb, which is what says the bar can be moved at all.
        //
        // align(CenterStart) is not decoration. The parent centres its children,
        // so a box occupying the filled fraction was centred in the bar rather
        // than starting at its left edge — putting the thumb at 55% over a video
        // twelve per cent through, with the red fill beside it disagreeing.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(shown)
                .height(24.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Tokens.brand))
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Box(
        modifier
            // 44dp of target around a 24dp glyph. Six of these sit in one row
            // on a phone; 48 each would not fit, and below 44 a moving thumb
            // misses and the picture underneath swallows the tap.
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size))
    }
}
