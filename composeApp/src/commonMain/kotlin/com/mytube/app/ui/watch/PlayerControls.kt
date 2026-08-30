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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onPlayPrevious: () -> Unit,
    onToggleSubtitles: () -> Unit,
    hasNext: Boolean,
    hasPrevious: Boolean,
    hasSubtitles: Boolean,
    subtitlesOn: Boolean,
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

                // Top row: collapse on the left, subtitles and settings on the
                // right. This is the arrangement YouTube's own player uses, and
                // it is here because it was asked for by name — the previous
                // version copied the *web* app's single bottom bar, which is
                // right for a page and cramped on a phone, where six controls
                // shared one row and the clock sat between them.
                //
                // No cast button. There is nothing to cast to: the library is
                // reached over the house wifi by IP, and a Cast receiver would
                // be a second server this project does not have.
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = Space.xs, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(
                        // A chevron down, not a back arrow: this collapses the
                        // video to the miniplayer rather than closing it, and
                        // an arrow would promise the opposite.
                        icon = ChevronIcon,
                        label = strings.back,
                        onClick = onBack,
                    )
                    Spacer(Modifier.weight(1f))
                    if (hasSubtitles) {
                        // On is marked by an underline, not a second glyph.
                        //
                        // The filled version of this icon was a white box with
                        // the letters knocked out in black — and `Icon` applies
                        // a *tint*, which repaints every path in one colour. The
                        // letters turned white with the box and the button
                        // became a solid white square. Anything two-toned inside
                        // an `Icon` has the same fault waiting in it.
                        Box(contentAlignment = Alignment.BottomCenter) {
                            ControlButton(
                                icon = CaptionsIcon,
                                label = strings.subtitles,
                                onClick = { onToggleSubtitles(); lastTouch++ },
                            )
                            if (subtitlesOn) {
                                Box(
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .width(22.dp)
                                        .height(2.dp)
                                        .background(Color.White),
                                )
                            }
                        }
                    }
                    ControlButton(
                        icon = SettingsGearIcon,
                        label = strings.settingsInPlayer,
                        onClick = { onOpenSettings(); lastTouch++ },
                    )
                }

                // The three transport controls, in the middle, on discs.
                //
                // The discs are the point: over a moving picture a bare glyph
                // disappears against whatever happens to be behind it, and the
                // middle of the frame is the one place that cannot be relied on
                // to be dark.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiscButton(
                        icon = PreviousIcon,
                        label = strings.playPrevious,
                        enabled = hasPrevious,
                        onClick = { onPlayPrevious(); lastTouch++ },
                    )
                    DiscButton(
                        icon = if (playback.isPlaying) PauseIcon else PlayIcon,
                        label = if (playback.isPlaying) strings.pause else strings.play,
                        enabled = true,
                        size = 64.dp,
                        onClick = { onPlayPause(); lastTouch++ },
                    )
                    DiscButton(
                        icon = NextIcon,
                        label = strings.playNext,
                        enabled = hasNext,
                        onClick = { onPlayNext(); lastTouch++ },
                    )
                }

                // The clock as a pill at the bottom left, fullscreen opposite,
                // and the bar itself along the very bottom edge.
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = Space.sm, end = Space.sm, bottom = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isLive) {
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
                        } else {
                            Text(
                                text = formatDuration(playback.positionSeconds.toInt()) +
                                    " / " + formatDuration(playback.durationSeconds.toInt()),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    ControlButton(
                        icon = if (fullscreen) ShrinkIcon else ExpandIcon,
                        label = if (fullscreen) strings.exitFullscreen else strings.fullscreen,
                        onClick = { onToggleFullscreen(); lastTouch++ },
                    )
                }

                if (!isLive) {
                    SeekBar(
                        progress = playback.progress,
                        enabled = playback.durationSeconds > 0,
                        onSeekFraction = {
                            onSeek(it * playback.durationSeconds)
                            lastTouch++
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/**
 * A transport control on a translucent disc.
 *
 * Disabled means drawn faint and not pressable, rather than absent: previous and
 * next keep their places whether or not there is anywhere to go, so the play
 * button does not move under a thumb that is already reaching for it.
 */
@Composable
private fun DiscButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 52.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(size * 0.46f),
        )
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
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableStateOf(1f) }
    // What the finger is on while it is down. The player's own progress keeps
    // arriving during a drag and would fight it — the thumb would jump back to
    // wherever playback is between frames.
    var dragging by remember { mutableStateOf(-1f) }
    val shown = if (dragging >= 0f) dragging else progress

    Box(
        modifier
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
