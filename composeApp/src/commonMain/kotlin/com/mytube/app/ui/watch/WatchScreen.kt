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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
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
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.mytube.app.ui.home.ChannelAvatar
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.home.formatCount
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.home.todayISO
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.shell.LocalGlassVisible
import com.mytube.app.ui.shell.WatchSkeleton
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.mytube.app.ui.theme.Tokens
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * How much faster the page under the picture fades than the finger moves.
 *
 * 2.2, so it is gone by 45% of the journey — which is where the picture has
 * narrowed enough for the miniplayer's own title to take over. Chosen from where
 * the two need to hand over, not tuned by eye.
 */
private const val CONTENT_FADE_RATE = 2.2f

/**
 * How much faster the controls over the picture fade than the finger moves.
 *
 * 6, so they are gone within a sixth of the drag — near enough to instant that
 * the gesture reads as "the video is being put away", while still a fade rather
 * than a disappearance.
 */
private const val OVERLAY_FADE_RATE = 6f

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
    /**
     * Opens the sheet asking which collections this video belongs in.
     *
     * The Save pill used to write the pinned bit directly. It now asks the
     * question that bit was standing in for; the sheet lives in `App.kt`
     * because six screens open the same one.
     */
    onSaveToPlaylist: (saved: Boolean) -> Unit,
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
        // The pill carries the pinned bit outward rather than the sheet asking
        // for it: the screen already has it, and a request for a fact in hand
        // is a slower sheet for nothing.
        onToggleSaved = {
            onSaveToPlaylist((state as? WatchState.Playing)?.video?.saved == true)
        },
        onToggleSubscribed = viewModel::toggleSubscribed,
        onToggleNarration = viewModel::toggleNarration,
        onToggleAutoplay = viewModel::toggleAutoplay,
        onSelectSubtitles = viewModel::selectSubtitles,
        // The ViewModel decides what next is, because the answer depends on the
        // list this video was opened from — see `WatchViewModel.nextId`. The
        // screen used to take the rail's first entry, which was wrong the moment
        // a channel page sorted by Popular played through.
        onPlayNext = {
            val next = (state as? WatchState.Playing)?.nextId.orEmpty()
            if (next.isNotEmpty()) onAdvanceTo(next)
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
    // How far through the drag-to-miniplayer gesture this screen is, and how
    // many pixels the picture has to cross to reach the bar.
    val drag = LocalDragProgress.current
    val travel = LocalDragTravel.current
    // Where the picture has to arrive across the screen, in this layer's own
    // pixels. Measured rather than assumed — see the picture's modifier below.
    val density = LocalDensity.current
    var pictureBoxWidth by remember { mutableStateOf(0f) }
    val miniThumbWidthPx = with(density) { MINI_THUMB_HEIGHT.toPx() }
    // The window is inset from the capsule's top edge, and `travel` ends at that
    // edge. Without this the picture stopped a thumb's padding short of the
    // circle it was heading for and jumped the last 8dp in one frame.
    val miniThumbTopPx = with(density) { MINI_THUMB_PAD.toPx() }
    val miniThumbLeftPx = with(density) { MINI_THUMB_LEFT.toPx() }
    val miniThumbFraction =
        if (pictureBoxWidth > 0f) (miniThumbWidthPx / pictureBoxWidth).coerceIn(0f, 1f) else 0.3f

    val openShare = rememberShare()
    val share: () -> Unit = {
        val id = (state as? WatchState.Playing)?.video?.id.orEmpty()
        if (id.isNotEmpty()) openShare("https://www.youtube.com/watch?v=$id")
    }

    // Told the platform as state, not as an event: a screen disposed while
    // fullscreen must not leave the phone sideways with no system bars.
    ApplyFullscreen(fullscreen)

    // The ground fades with the drag rather than staying solid.
    //
    // This was painting an opaque `Tokens.bg` *inside* the layer that was busy
    // fading its own background to let the tab through — so the tab never
    // appeared, and what a dragging finger uncovered was a flat dark rectangle.
    // Two backgrounds, one of them fading, and the one on top winning.
    // What the settings sheet blurs.
    //
    // Its own state, not the shell's `LocalHaze`. That one is registered on the
    // tab content, and the watch screen is a sibling drawn *over* it — a sheet
    // reading it would frost the feed hiding behind this page rather than the
    // page itself. Everything above the sheet in this screen is inside it.
    val sheetBackdrop = rememberLayerBackdrop {
        drawRect(Tokens.bg)
        drawContent()
    }

    // A Box, so the sheet can be a full-screen overlay of this screen.
    //
    // It was a `ModalBottomSheet`, which portals to the window and therefore sat
    // over the picture even in fullscreen, wherever it happened to be written.
    // In the scene it has to be a sibling of the whole page and drawn last, or
    // it opens underneath the thing it belongs to.
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .layerBackdrop(sheetBackdrop)
            .background(if (fullscreen) Tokens.bg else Tokens.bg.copy(alpha = 1f - drag)),
    ) {
        // No status-bar gap in fullscreen — there is no status bar, and the gap
        // would be a black band where the picture should be.
        if (!fullscreen) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
        }

        // Two boxes, and the outer one never changes size.
        //
        // That separation is the fix for a plain fault: the picture shrank by
        // *layout*, so the space it gave up was taken by whatever was below it
        // — the title and the channel row slid up under the video as it was
        // being dragged away, and ended up printed across it. The outer box
        // holds the full 16:9 slot open for the whole gesture, so nothing under
        // the picture moves at all; the inner one is what actually shrinks and
        // travels.
        //
        // The inner box resizes for real rather than being scaled by a
        // `graphicsLayer`. On iOS the surface is a `UIKitView`, and a scaled
        // interop layer is not a resized one — the video would be drawn at the
        // wrong size inside a correctly sized frame. Only `translationY` is a
        // transform, and it moves the whole thing without re-laying anything out
        // on the frames where that would be felt.
        Box(
            if (fullscreen) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .onSizeChanged { pictureBoxWidth = it.width.toFloat() }
            },
        ) {
        Box(
            if (fullscreen) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier
                    .align(Alignment.TopStart)
                    // translationY rather than padding: this runs on every frame
                    // of a finger drag, and a layout pass per frame is the one
                    // thing that makes a gesture feel heavy.
                    .graphicsLayer {
                        translationY = drag * (travel + miniThumbTopPx)
                        // And sideways, because the bar is a floating capsule
                        // now: its picture starts a margin plus a padding in
                        // from the screen's edge, not at zero. Without this the
                        // video lands to the left of the box it is heading for
                        // and snaps across at the end.
                        translationX = drag * miniThumbLeftPx
                    }
                    // `drag` is 0 whenever nothing is being dragged, so this is
                    // the ordinary 16:9 box the rest of the time.
                    //
                    // The end fraction is measured, not guessed: it is the
                    // capsule's own window against this layer's actual width, so
                    // it is right on a phone and on a tablet without a second
                    // constant to keep in step.
                    .fillMaxWidth(lerp(1f, miniThumbFraction, drag))
                    // And the shape travels too, 16:9 to square, because what it
                    // is landing in is a circle. Without this the picture
                    // arrives as a wide frame and is replaced by a round one in
                    // a single frame — the handover the whole gesture exists to
                    // hide. The surface crops from the moment the drag starts,
                    // for the same reason.
                    .aspectRatio(lerp(16f / 9f, 1f, drag))
                    // And the corners round as it goes, 0 to a circle.
                    //
                    // The box is square by the end — that is what the aspect
                    // ratio above does — so a 50% corner is a circle exactly
                    // when it arrives, and anything less than the whole journey
                    // means the last frame swaps a rounded rectangle for a round
                    // window. `percent` rather than a radius in dp for the same
                    // reason: the box is shrinking, and a fixed radius would be
                    // a different proportion of it in every frame.
                    .clip(RoundedCornerShape(percent = (50f * drag).roundToInt()))
                    .background(Color.Black)
            },
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is WatchState.Playing -> if (player != null) {
                    VideoSurface(player, Modifier.fillMaxSize(), fill = drag > 0f)

                    // Everything drawn *on* the picture goes as soon as the drag
                    // starts, and quickly — gone by a sixth of the journey.
                    //
                    // They are sized for a full-width player and none of them
                    // scales with it: a caption set for a phone's width is
                    // printed across a thumbnail three tenths as wide, and the
                    // transport discs end up larger than the picture they are
                    // over. What a dragging finger should see is the video
                    // travelling, not a control bar riding it down.
                    // The same disappearance, said again for the panes the
                    // platform draws.
                    //
                    // `graphicsLayer` fades what *Compose* renders, and on iOS 26
                    // the controls over the picture are not that — they are drawn
                    // above the whole scene by SwiftUI, where an alpha set here
                    // cannot reach them. Reported from the phone: the video shrank
                    // away and the buttons rode down over it at full strength.
                    CompositionLocalProvider(LocalGlassVisible provides (drag == 0f)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = (1f - drag * OVERLAY_FADE_RATE)
                                    .coerceAtLeast(0f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                    // Between the picture and the control bar: the bar covers
                    // the bottom of the frame, which is where captions go, and
                    // it wins the overlap for the few seconds it is visible.
                    SubtitleOverlay(
                        cues = state.subtitleCues,
                        positionSeconds = state.playback.positionSeconds,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    // Not a bare spinner. A ring alone over a black rectangle is
                    // the same picture as a video that has failed, and telling
                    // those two apart with no words is something a viewer cannot
                    // do — so it says which one it is. YouTube's own player does
                    // the same thing for the same reason.
                    if (state.playback.isBuffering) BufferingBadge(strings.loadingVideo)
                    PlayerControls(
                        playback = state.playback,
                        isLive = state.isLive,
                        fullscreen = fullscreen,
                        title = state.video.title,
                        channel = state.video.channel.name,
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
                        settingsOpen = settingsOpen,
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
                        hasNext = state.nextId.isNotEmpty(),
                        hasPrevious = hasPrevious,
                        hasSubtitles = state.video.subtitles.isNotEmpty(),
                        subtitlesOn = state.subtitleLanguage.isNotEmpty(),
                    )
                    }
                    }
                }

                // Nothing here: the skeleton below draws the whole screen,
                // picture included, so a spinner over it would be two things
                // saying the same thing in different words.
                is WatchState.Loading -> Unit

                // Every other state still gets the back arrow. Without it a
                // video that will not play is a screen with no way out but the
                // system gesture — and on a members-only video that is exactly
                // where somebody lands.
                else -> BackOnly(onBack, strings.back)
            }
        }

        // The title and the channel used to travel with the picture here — a
        // row whose left edge tracked the shrinking thumbnail's right edge all
        // the way down. It is gone: the miniplayer's own bar is now drawn from
        // the first pixel of the drag and carries that text itself, so keeping
        // this would print the same title twice, one copy sliding over the
        // other. Text that is already in its final place does not need a second
        // copy flying to meet it.

        }

        if (fullscreen) return@Column

        // Everything under the picture fades out through the drag, and faster
        // than the drag itself.
        //
        // `1f - drag` was too slow to watch: a third of the way down, the page
        // was still two-thirds opaque and the picture had travelled onto it, so
        // the video was printed across its own title. The page has to be gone by
        // the time the picture reaches it, not when the gesture ends.
        Box(
            Modifier.graphicsLayer {
                alpha = (1f - drag * CONTENT_FADE_RATE).coerceAtLeast(0f)
            },
        ) {
        when (state) {
            is WatchState.Loading -> WatchSkeleton()

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
            //
            // The reason is *translated*, not the wire token. It used to print
            // `state.reason` straight through, so a members-only video said
            // "members_only" — a word that reports a fault and explains
            // nothing. See [unavailableCopy].
            is WatchState.Unavailable -> Message(
                title = strings.unavailableTitle,
                detail = unavailableCopy(state.reason, strings),
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

    // Drawn last, over the whole page, so it is on top of it.
    //
    // Shown in fullscreen too. It was suppressed there once, which left the gear
    // on the control bar doing nothing at all as soon as the video filled the
    // screen — a dead button, and the one thing §5 of the server charter forbids
    // outright. In fullscreen there is no Compose-drawn content behind it, so
    // its glass falls back to the wash, which is the same thing that happens in
    // a Preview.
    if (state is WatchState.Playing) {
        PlayerSettingsPanel(
                visible = settingsOpen,
                backdrop = sheetBackdrop,
                bottomInset = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding(),
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
        // 40 on the watch page, against 36 in a card and 24 on a comment. From
        // the design system, and the differences are deliberate there.
        ChannelAvatar(
            name = video.channel.name,
            mediaBaseUrl = mediaBaseUrl,
            path = video.channel.avatarPath,
            size = 40.dp,
            modifier = Modifier.clickable(onClick = onOpenChannel),
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

/**
 * "Loading video…" on a disc, over the picture.
 *
 * Centred rather than in a corner: while this is up there is nothing else on
 * the frame to look at, and a message in the corner of a black rectangle reads
 * as a stray label rather than as the state of the thing.
 */
@Composable
private fun BufferingBadge(label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdaptiveCircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Space.sm))
        Text(label, color = Color.White, fontSize = 13.sp)
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
