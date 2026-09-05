package com.mytube.app.ui.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.i18n.LocalStrings
import androidx.compose.runtime.CompositionLocalProvider
import com.mytube.app.ui.shell.rememberSelectionTick
import com.mytube.app.ui.shell.GlassItem
import com.mytube.app.ui.shell.LocalGlassVisible
import com.mytube.app.ui.shell.GlassPane
import com.mytube.app.ui.shell.GlassPaneShape
import com.mytube.app.ui.shell.glassSurface
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

/**
 * How long the controls take to come and go.
 *
 * Explicit rather than the default spring, because **`PlayerGlass.swift` runs
 * the same fade for the panes it draws** and a spring has no duration to agree
 * with. It is the second number written down twice in this app for that reason,
 * the first being the bars' 220ms in `ShellBridge`, and like that one both
 * copies say so.
 *
 * What it fixes was reported from the phone: in fullscreen the seek bar faded
 * out and the buttons stayed a beat longer, because the platform pane left only
 * when Compose disposed it — after its own fade had finished.
 */
private const val CONTROLS_FADE_MILLIS = 200

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
    /**
     * The video's title and channel, drawn **only in fullscreen**.
     *
     * Everywhere else they are already the first thing under the picture, and a
     * second copy over it is the same fault just removed from the miniplayer's
     * drag: one piece of text printed twice, in two places, disagreeing about
     * where it belongs. Fullscreen covers that page, so this is the only state
     * in which the screen cannot otherwise say what is playing.
     */
    title: String,
    channel: String,
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
    /**
     * Whether the player's own settings sheet is up.
     *
     * Read for one thing only, and only where a platform layer draws the glass:
     * that layer is above everything Compose renders, so in fullscreen — the one
     * arrangement where the sheet overlaps the picture — the controls would
     * float on top of the sheet they opened. Reported from the phone exactly
     * that way. Nothing changes on Android, where the sheet is Compose and drawn
     * last, which is what puts it above them already.
     */
    settingsOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var visible by remember { mutableStateOf(true) }
    // Bumped on every interaction. The timer keys on it, so touching anything
    // restarts the countdown rather than letting the controls vanish under a
    // finger that is still using them.
    var lastTouch by remember { mutableStateOf(0) }
    // Where the finger is on the bar while it is down, as a fraction, or -1.
    //
    // Hoisted out of the bar because the whole screen answers to it: the
    // controls step out of the way, and the time being aimed at is drawn in the
    // middle of the picture rather than in the corner where the clock lives.
    var scrub by remember { mutableStateOf(-1f) }
    val scrubbing = scrub >= 0f
    // What the bar has to clear at the bottom of the screen in fullscreen. Zero
    // on a phone with hardware buttons, 34dp on one with a home indicator.
    val navigationInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    // And what everything has to clear at the *sides*.
    //
    // In portrait these are zero, which is why nothing needed them until the
    // player was turned: landscape on a phone with a Dynamic Island puts a real
    // inset on both edges, and the screen's corners are round on top of that. A
    // row padded with a flat 8dp then has its first and last control clipped —
    // reported from an iPhone 16e, and invisible on the simulator's flat
    // screenshot until you look for it.
    //
    // Added to the paddings rather than applied to the scrim: the scrim is the
    // darkening over the picture and has to reach the edges, while the controls
    // must not.
    val sides = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal)
        .asPaddingValues()
    val direction = LocalLayoutDirection.current
    val safeStart = sides.calculateStartPadding(direction)
    val safeEnd = sides.calculateEndPadding(direction)

    LaunchedEffect(visible, lastTouch, playback.isPlaying) {
        // A paused video keeps its controls. Hiding them leaves a still frame
        // with no sign the app is even running, and the one thing somebody
        // paused for is usually the button to start again.
        if (!visible || !playback.isPlaying) return@LaunchedEffect
        kotlinx.coroutines.delay(HIDE_AFTER_MILLIS)
        visible = false
    }

    // The double-tap jump, and the badge that says what it did.
    //
    // `taps` accumulates so a run reads 10, 20, 30 rather than flashing "10"
    // three times, and it is cleared by a coroutine rather than by the fade:
    // how it looks and how long it may still be added to are two different
    // durations, and tying them together makes a badge that cannot be caught.
    var ripple by remember { mutableStateOf(SeekRipple()) }
    LaunchedEffect(ripple.taps) {
        if (!ripple.visible) return@LaunchedEffect
        kotlinx.coroutines.delay(RIPPLE_LINGER_MILLIS)
        // The side is carried over, not defaulted: it is what the fade-out is
        // still drawn on.
        ripple = ripple.copy(seconds = 0)
    }

    Box(
        modifier
            .fillMaxSize()
            // One `detectTapGestures` with both, not a clickable beside it.
            //
            // Two recognisers would each see the first tap and the toggle would
            // fire *and* the jump — which is the fault this screen already paid
            // for once on the seek bar, where a tap derived from a drag stopped
            // working. Here it costs the opposite: the single tap waits for the
            // double-tap window to expire before showing the controls, about
            // 300ms. That delay is the price of the two gestures never
            // disagreeing, and it is what the reference does too.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        visible = !visible
                        lastTouch++
                    },
                    onDoubleTap = { offset ->
                        val forward = offset.x >= size.width / 2f
                        val step = if (forward) SKIP_SECONDS.toInt() else -SKIP_SECONDS.toInt()
                        onSkip(if (forward) SKIP_SECONDS else -SKIP_SECONDS)
                        // Only added to while the run is going the same way. A
                        // tap on the other side is a new intention, not
                        // twenty seconds less of this one.
                        val running = if (ripple.visible && ripple.forward == forward) {
                            ripple.seconds
                        } else {
                            0
                        }
                        ripple = SeekRipple(running + step, forward, ripple.taps + 1)
                        // Deliberately not touching `visible`. Double-tapping a
                        // bare picture on the reference jumps and leaves it
                        // bare; showing the controls here would put the transport
                        // discs under a finger that is still tapping.
                        lastTouch++
                    },
                )
            },
    ) {
        // Under the controls, over the picture. A finger doing this is often
        // still on the screen, so it must take no pointer events of its own —
        // see the note on `SeekRippleOverlay`.
        SeekRippleOverlay(ripple, Modifier.matchParentSize())
        // Out of the way while scrubbing. YouTube does this and the reason is
        // plain on a phone: the transport discs sit in the middle of the frame,
        // which is the half of the picture somebody dragging the bar is trying
        // to see.
        // Provided once, read by all seven panes. See [LocalGlassVisible].
        //
        // The outer value is *and*-ed rather than replaced: the watch screen
        // turns it off for the whole drag to the miniplayer, and this composable
        // knows nothing about that gesture. Replacing it would light the buttons
        // back up halfway down the screen.
        CompositionLocalProvider(
            LocalGlassVisible provides (
                LocalGlassVisible.current &&
                    visible &&
                    !scrubbing &&
                    !(fullscreen && settingsOpen)
                ),
        ) {
        AnimatedVisibility(
            visible && !scrubbing && !(fullscreen && settingsOpen),
            enter = fadeIn(tween(CONTROLS_FADE_MILLIS)),
            exit = fadeOut(tween(CONTROLS_FADE_MILLIS)),
        ) {
            // No scrim.
            //
            // There was one — black at 0.4 over the whole frame — and the note
            // beside it already said what was wrong with it: *"white glyphs over
            // a bright frame are unreadable, and darkening the whole picture to
            // fix that is punishing the video for the controls."* It was the
            // answer available before the controls were made of glass. Now every
            // glyph on this screen sits on a pane of its own, so the pane can do
            // the work the scrim was doing and the picture is left alone.
            //
            // That is why the chevron and the fullscreen title gained panes in
            // the same change: they were the two things still floating bare, and
            // they were legible only because the whole frame was being dimmed
            // for them.
            Box(Modifier.fillMaxSize()) {

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
                        .padding(
                            start = Space.xs + safeStart,
                            end = Space.xs + safeEnd,
                            top = Space.xs,
                            bottom = Space.xs,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassPane("player-back", GlassPaneShape.Capsule) {
                        GlassItem(
                            id = "back",
                            symbol = "chevron.down",
                            onPress = onBack,
                        ) {
                            ControlButton(
                                // A chevron down, not a back arrow: this
                                // collapses the video to the miniplayer rather
                                // than closing it, and an arrow would promise
                                // the opposite.
                                icon = ChevronIcon,
                                label = strings.back,
                                width = 48.dp,
                                onClick = onBack,
                            )
                        }
                    }
                    if (fullscreen) {
                        // `weight` on the text rather than on a Spacer, so a long
                        // title is truncated instead of pushing the cluster on the
                        // right off the edge of a landscape phone.
                        GlassPane(
                            id = "player-title",
                            shape = GlassPaneShape.Capsule,
                            modifier = Modifier.weight(1f).padding(horizontal = Space.sm),
                        ) {
                        Column(Modifier.padding(horizontal = Space.md, vertical = 6.dp)) {
                            GlassItem(
                                id = "title",
                                text = title,
                                pointSize = 15.0,
                                bold = true,
                            ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            }
                            GlassItem(
                                id = "title-channel",
                                text = channel,
                                pointSize = 13.0,
                                opacity = 0.7,
                            ) {
                            Text(
                                text = channel,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            }
                        }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    // The two on the right share one pill.
                    //
                    // Two glyphs side by side with nothing around them read as
                    // two unrelated marks on the picture; in a pill they read as
                    // one cluster of controls, which is what they are. The pill
                    // is also where the extra width comes from — the gear was
                    // reported as hard to hit, and each button inside is 56dp
                    // wide against the 48 it had.
                    //
                    // And it is the first pane whose glass the *platform* draws
                    // where it can — see [GlassPane]. The layout below is
                    // unchanged and still Compose's; on iOS 26 it is measured,
                    // placed, and painted by SwiftUI instead, which is the only
                    // arrangement in which this pane can actually blur the video
                    // behind it.
                    GlassPane("player-top-right", GlassPaneShape.Capsule) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSubtitles) {
                        // On is marked by an underline, not a second glyph.
                        //
                        // The filled version of this icon was a white box with
                        // the letters knocked out in black — and `Icon` applies
                        // a *tint*, which repaints every path in one colour. The
                        // letters turned white with the box and the button
                        // became a solid white square. Anything two-toned inside
                        // an `Icon` has the same fault waiting in it.
                        GlassItem(
                            id = "cc",
                            symbol = "captions.bubble",
                            on = subtitlesOn,
                            onPress = { onToggleSubtitles(); lastTouch++ },
                        ) {
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
                    }
                    GlassItem(
                        id = "gear",
                        symbol = "gearshape",
                        onPress = { onOpenSettings(); lastTouch++ },
                    ) {
                        ControlButton(
                            icon = SettingsGearIcon,
                            label = strings.settingsInPlayer,
                            onClick = { onOpenSettings(); lastTouch++ },
                        )
                    }
                    }
                    }
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
                        id = "previous",
                        symbol = "backward.end.fill",
                        icon = PreviousIcon,
                        label = strings.playPrevious,
                        enabled = hasPrevious,
                        onClick = { onPlayPrevious(); lastTouch++ },
                    )
                    DiscButton(
                        id = "play",
                        symbol = if (playback.isPlaying) "pause.fill" else "play.fill",
                        icon = if (playback.isPlaying) PauseIcon else PlayIcon,
                        label = if (playback.isPlaying) strings.pause else strings.play,
                        enabled = true,
                        size = 64.dp,
                        onClick = { onPlayPause(); lastTouch++ },
                    )
                    DiscButton(
                        id = "next",
                        symbol = "forward.end.fill",
                        icon = NextIcon,
                        label = strings.playNext,
                        enabled = hasNext,
                        onClick = { onPlayNext(); lastTouch++ },
                    )
                }

                // The clock as a pill at the bottom left, fullscreen opposite.
                //
                // The bottom padding clears the seek bar's **32dp target**, not
                // its 3dp line — a row sitting 12dp up was inside the bar's
                // reach even though nothing looked as though it touched.
                //
                // And in fullscreen it has to clear the bar's own inset too.
                // That was missed when the bar moved up off the screen edge:
                // the row kept a flat 34dp while the bar rose to
                // `navigationInset + 16`, so the two ended up on the same line
                // and the shrink button sat on top of the slider. The number is
                // computed from the same three terms the bar is placed with,
                // rather than being a constant that has to be remembered twice.
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = Space.sm + safeStart,
                            end = Space.sm + safeEnd,
                            bottom = if (fullscreen) {
                                navigationInset + Space.lg + SEEK_TARGET + Space.sm
                            } else {
                                34.dp
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassPane("player-clock", GlassPaneShape.Capsule) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isLive) {
                            // Rewound inside the window, the pill stops being a
                            // readout and becomes the way back: the dot goes
                            // grey, the label carries how far behind the picture
                            // is, and pressing it returns to the edge. At the
                            // edge there is nowhere to go, so it is a readout
                            // again — the web app's own arrangement, and the
                            // reason there is no separate button beside it.
                            val behind = playback.liveEndSeconds - playback.positionSeconds
                            val atEdge = playback.atLiveEdge || !playback.hasLiveWindow
                            val label = if (atEdge) {
                                strings.live
                            } else {
                                strings.live + " · −" + formatDuration(behind.toInt())
                            }
                            val goLive: () -> Unit = {
                                onSeek(playback.liveEdgeTarget)
                                lastTouch++
                            }
                            // The colour travels rather than being named on
                            // the far side: one definition, in `Tokens`. And it
                            // has to travel, because on iOS 26 this dot is drawn
                            // by SwiftUI — a Compose background here is what
                            // Android sees and the platform pane ignores, so
                            // setting only that left the dot red while the label
                            // beside it read "−50:34". Measured.
                            val dotColour = if (atEdge) {
                                Tokens.brand
                            } else {
                                Color.White.copy(alpha = 0.4f)
                            }
                            GlassItem(
                                id = "live-dot",
                                dot = true,
                                tintArgb = dotColour.value.toLong() ushr 32,
                            ) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(dotColour),
                            )
                            }
                            Spacer(Modifier.width(Space.sm))
                            GlassItem(
                                id = "live-label",
                                text = label,
                                pointSize = 13.0,
                                bold = true,
                                onPress = if (atEdge) null else goLive,
                            ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = if (atEdge) {
                                    Modifier
                                } else {
                                    Modifier.clickable(onClick = goLive)
                                },
                            )
                            }
                        } else {
                            val clock = formatDuration(playback.positionSeconds.toInt()) +
                                " / " + formatDuration(playback.durationSeconds.toInt())
                            GlassItem(id = "clock", text = clock, pointSize = 13.0) {
                            Text(text = clock, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                    }
                    Spacer(Modifier.weight(1f))
                    GlassPane("player-zoom", GlassPaneShape.Capsule) {
                        GlassItem(
                            id = "zoom",
                            symbol = if (fullscreen) {
                                "arrow.down.right.and.arrow.up.left"
                            } else {
                                "arrow.up.left.and.arrow.down.right"
                            },
                            onPress = { onToggleFullscreen(); lastTouch++ },
                        ) {
                            ControlButton(
                                icon = if (fullscreen) ShrinkIcon else ExpandIcon,
                                label = if (fullscreen) {
                                    strings.exitFullscreen
                                } else {
                                    strings.fullscreen
                                },
                                width = 48.dp,
                                onClick = { onToggleFullscreen(); lastTouch++ },
                            )
                        }
                    }
                }
            }
        }
        }

        // Outside the picture the bar lives outside the scrim, so it is there
        // whether or not the controls are.
        //
        // That is what the platform does and it is the whole shape of this
        // control: at rest it is a hairline along the very bottom edge of the
        // *frame*, saying how far through the video is; touched, it grows and
        // takes a finger. It used to appear and disappear with the controls,
        // which left a playing video with nothing on screen to say where it was.
        //
        // **In fullscreen that reasoning has no subject.** There is no frame for
        // the hairline to be the edge of — it is a stripe floating across the
        // bottom of the screen with the buttons that explain it already faded
        // out, which is how it was reported. So in fullscreen it goes up and
        // down with everything else, and it moves in from the edges: at the very
        // bottom of an iPhone it shares its 32dp target with the home
        // indicator's swipe, and a finger seeking would leave the app.
        // Drawn for a broadcast too, once the player has reported a window.
        //
        // It used to be refused outright, and the reasoning was sound about the
        // arithmetic and wrong about the conclusion: a broadcast declares no
        // duration, so `position / duration` came to 155,700% on the web — a bar
        // solid red from the first second. What a live playlist *does* declare
        // is its rewindable window, which this server measured at 0..3605 on one
        // broadcast and 0..1285 on another. `progress` is measured against that
        // window, and until one has arrived `hasLiveWindow` is false and nothing
        // is drawn — which is what this screen did for every broadcast before.
        if (!isLive || playback.hasLiveWindow) {
            val seekBar: @Composable () -> Unit = {
                SeekBar(
                    progress = playback.progress,
                    enabled = playback.hasLiveWindow || playback.durationSeconds > 0,
                    expanded = visible,
                    scrub = scrub,
                    onScrub = { scrub = it },
                    onSeekFraction = {
                        // A fraction of the window for a broadcast, of the
                        // length for a file — and clamped short of the window's
                        // very end, which a seek cannot land on. The arithmetic
                        // is on `PlaybackState` rather than here so it can be
                        // tested without a player, and so the bar and the LIVE
                        // pill cannot disagree about where the edge is.
                        onSeek(playback.seekTarget(it))
                        lastTouch++
                    },
                    onInteract = { lastTouch++ },
                    modifier = if (fullscreen) {
                        // The real inset, not 34dp: an iPhone with a home
                        // indicator reports 34 and a phone with buttons reports
                        // 0, and a constant is wrong on exactly one of them.
                        Modifier.padding(
                            start = Space.lg + safeStart,
                            end = Space.lg + safeEnd,
                            bottom = Space.lg + navigationInset,
                        )
                    } else {
                        Modifier
                    },
                )
            }

            if (fullscreen) {
                // Same condition as the controls, so the two cannot disagree
                // about whether the player is on screen.
                AnimatedVisibility(
                    visible = visible || scrubbing,
                    enter = fadeIn(tween(CONTROLS_FADE_MILLIS)),
                    exit = fadeOut(tween(CONTROLS_FADE_MILLIS)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    seekBar()
                }
            } else {
                Box(Modifier.align(Alignment.BottomCenter)) { seekBar() }
            }
        }

        // The time being aimed at, in the middle of the picture.
        //
        // In the middle because that is where the eye already is — the corner
        // pill is a readout of where playback *is*, and while a finger is on the
        // bar the question is where it is going. Only while scrubbing: two
        // clocks disagreeing by a minute is the state this replaces.
        if (scrubbing) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = Space.lg, vertical = Space.sm),
            ) {
                Text(
                    text = formatDuration((scrub * playback.durationSeconds).toInt()),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
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
    id: String,
    symbol: String,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 52.dp,
) {
    // A pane each, rather than one pane behind all three. They are three
    // separate discs with 28dp of picture between them, which is the reference's
    // arrangement — a single pane would be a bar, and the video would stop
    // showing between the buttons.
    GlassPane("player-$id", GlassPaneShape.Capsule) {
        GlassItem(
            id = id,
            symbol = symbol,
            // The glyph inside a disc is proportional to it, which is how the
            // 64dp play button reads as the larger one rather than as a bigger
            // circle around the same mark.
            pointSize = (size.value * 0.46f).toDouble(),
            opacity = if (enabled) 1.0 else 0.35,
            // Faint *and* dead. A disabled control that still reported presses
            // would be the dead button §5 of the server charter forbids, in the
            // one shape nothing on screen distinguishes.
            onPress = if (enabled) onClick else null,
        ) {
            Box(
                Modifier
                    .size(size)
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
    }
}

/**
 * The bar, and the only thing here that takes a drag.
 *
 * ## Three states, one control
 *
 * - **At rest**: a hairline along the very bottom edge of the picture. No thumb.
 *   It is a readout, and a thumb on a line nobody is touching is an invitation
 *   to a gesture that is already available anywhere along it.
 * - **Controls up**: still just the line. A knob on a bar nobody is touching is
 *   an invitation to a gesture that is already available anywhere along it.
 * - **Under a finger**: the track thickens and the knob grows, which is the only
 *   feedback a seek has before the picture catches up — and the picture does not
 *   catch up until the finger lifts.
 *
 * ## Why it is not disabled before the duration is known
 *
 * It is. A bar that can be dragged before the player knows how long the video is
 * computes a target from zero, which is a seek to zero — the video restarting is
 * what a viewer sees, and nothing explains it.
 *
 * ## The notches
 *
 * A tick of haptic feedback every hundredth of the bar. Not every second: on a
 * three-hour video a finger sweeping the width would cross ten thousand of them,
 * and a continuous buzz is not feedback. A hundred notches feels the same on a
 * two-minute clip and on a film, which is what makes the control feel like one
 * control.
 */
@Composable
private fun SeekBar(
    progress: Float,
    enabled: Boolean,
    /** The controls are up, so the bar shows it can be grabbed. */
    expanded: Boolean,
    /** Where the finger is, 0..1, or -1 when nothing is touching it. */
    scrub: Float,
    onScrub: (Float) -> Unit,
    onSeekFraction: (Double) -> Unit,
    /**
     * Called on every movement of the finger.
     *
     * Without it the five-second timer kept running through a slow drag and the
     * whole control bar — the one being dragged — faded out from under the
     * thumb. That is most of "it is hard to seek while the video is playing":
     * the target was not small, it was disappearing.
     */
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableStateOf(1f) }
    val dragging = scrub >= 0f
    // The player's own progress keeps arriving during a drag and would fight it,
    // so the finger wins while it is down.
    val shown = if (dragging) scrub else progress

    val tick = rememberSelectionTick()
    // The notch last reported, so one is felt per crossing rather than per frame.
    var lastNotch by remember { mutableStateOf(-1) }

    // Where the finger is, kept *inside* the gesture as well as hoisted.
    //
    // `pointerInput` keys on `enabled`, so its block captures the values it was
    // composed with and keeps them for the life of the gesture detector. Reading
    // the hoisted `scrub` from inside `onDragEnd` therefore read -1 for ever —
    // the guard never passed, and letting go of the bar applied no seek at all.
    // The hoisted copy still exists because the rest of the screen draws from
    // it; this one is what the gesture decides with.
    val here = remember { mutableStateOf(-1f) }

    fun move(x: Float) {
        val fraction = (x / width).coerceIn(0f, 1f)
        here.value = fraction
        onScrub(fraction)
        onInteract()
        val notch = (fraction * NOTCHES).toInt()
        if (notch != lastNotch) {
            lastNotch = notch
            tick()
        }
    }

    val track by animateDpAsState(
        targetValue = when {
            dragging -> TRACK_DRAGGING
            expanded -> TRACK_EXPANDED
            else -> TRACK_RESTING
        },
        label = "seek-track",
    )
    // Nothing to grab until something grabs it. A knob sitting on a line nobody
    // is touching invites a gesture that is already available anywhere along the
    // bar, and it is one more thing over the picture.
    val knob by animateDpAsState(
        targetValue = if (dragging) KNOB_DRAGGING else 0.dp,
        label = "seek-knob",
    )

    Box(
        modifier
            .fillMaxWidth()
            // A 3dp line is nothing to aim at. The target is 32dp with the line
            // along its bottom edge — 24 was the number before, and on a moving
            // video with a thumb arriving at an angle it was measurably missable.
            //
            // Named, because the row above the bar has to keep clear of *this*
            // and not of the line: two copies of the number is how the shrink
            // button ended up drawn on top of the slider.
            .height(SEEK_TARGET)
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
                    onDragStart = { move(it.x) },
                    onDragEnd = {
                        if (here.value >= 0f) onSeekFraction(here.value.toDouble())
                        here.value = -1f
                        onScrub(-1f)
                        lastNotch = -1
                    },
                    onDragCancel = {
                        here.value = -1f
                        onScrub(-1f)
                        lastNotch = -1
                    },
                    onHorizontalDrag = { change, _ -> move(change.position.x) },
                )
            },
        // Along the bottom edge, not centred in the target. At rest the line is
        // the boundary of the picture, which is where every player draws it.
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(track)
                .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(Tokens.brand))

            // The knob rides the filled portion's right edge, **inside the
            // track** so it is centred on the line rather than resting on it.
            //
            // It was a sibling of the track anchored to the bottom of the touch
            // target, which put its centre `knob/2` above the bottom while the
            // line's centre is `track/2` above it — eight points of daylight,
            // and the knob visibly floating over the bar. A Box does not clip,
            // so a 22dp circle inside a 6dp track overflows symmetrically, which
            // is exactly what centring means here.
            if (knob > 0.dp) {
                Box(
                    Modifier.fillMaxWidth(shown).fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(Modifier.size(knob).clip(CircleShape).background(Tokens.brand))
                }
            }
        }
    }
}

/**
 * How tall the bar's touch target is — the line itself is [TRACK_RESTING].
 *
 * Read in two places: by the bar, and by the row above it, which must not
 * overlap it.
 */
private val SEEK_TARGET = 32.dp

/** A hairline at rest; a line to grab when the controls are up; a bar under a finger. */
private val TRACK_RESTING = 3.dp
private val TRACK_EXPANDED = 3.dp
private val TRACK_DRAGGING = 6.dp
private val KNOB_DRAGGING = 22.dp

/** How many notches the width is divided into, for the feedback. */
private const val NOTCHES = 100

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    /**
     * 56 by default, 48 for a button drawn inside a circle of its own.
     *
     * Wider than it is tall, and only wider: the gear was reported as hard to
     * hit, and the room to fix that is horizontal — the row runs across the top
     * of the picture with space to spare, while growing it downward would reach
     * into the frame and, at the bottom of the screen, into the seek bar's own
     * 32dp target. The glyph stays 24dp; what changed is what counts as a hit.
     */
    width: Dp = 56.dp,
) {
    Box(
        modifier
            .size(width = width, height = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size))
    }
}
