package com.mytube.app.ui.tv

import com.mytube.app.ui.shell.FocusTrail
import com.mytube.app.ui.shell.LocalFocusMemory
import com.mytube.app.ui.shell.awaitPlaced
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.mytube.app.ui.home.videoMenu
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.StoryboardState
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.ui.home.ChannelAvatar
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDate
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.home.formatViews
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.FOCUS_MAX_OUTSET
import com.mytube.app.ui.shell.LocalTelevision
import com.mytube.app.ui.shell.TV_SAFE_HORIZONTAL
import com.mytube.app.ui.shell.TV_SAFE_VERTICAL
import com.mytube.app.ui.shell.focusStop
import com.mytube.app.ui.shell.pressable
import com.mytube.app.ui.shell.pressableGlassControl
import com.mytube.app.ui.shell.pressableGlassSurface
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.watch.CaptionsIcon
import com.mytube.app.ui.watch.CommentsIcon
import com.mytube.app.ui.watch.DescriptionIcon
import com.mytube.app.ui.watch.KeepScreenOn
import com.mytube.app.ui.watch.NextIcon
import com.mytube.app.ui.watch.PauseIcon
import com.mytube.app.ui.watch.PlayIcon
import com.mytube.app.ui.watch.PreviousIcon
import com.mytube.app.ui.watch.SaveFilledIcon
import com.mytube.app.ui.watch.SaveIcon
import com.mytube.app.ui.watch.NarrationFilledIcon
import com.mytube.app.ui.watch.NarrationIcon
import com.mytube.app.ui.watch.ScrubPreview
import com.mytube.app.ui.watch.SubtitleOverlay
import com.mytube.app.ui.watch.ThumbFilledIcon
import com.mytube.app.ui.watch.ThumbIcon
import com.mytube.app.ui.watch.VideoSurface
import com.mytube.app.ui.watch.WatchState
import com.mytube.app.ui.watch.WatchViewModel
import com.mytube.app.ui.watch.trackLabel
import com.mytube.app.ui.watch.unavailableCopy
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The television's player: the picture, the whole screen, always.
 *
 * # Why it is not the phone's watch screen with a flag
 *
 * The phone's is a page *under* a picture — title, actions, comments and the up
 * next rail scrolling below a 16:9 slot — and a layer that a finger drags down
 * into a miniplayer. Neither half survives a remote. There is no finger to drag,
 * and a page scrolled with a D-pad under a picture that shrinks to fit it is a
 * television showing a phone's screen. What YouTube's TV app does instead is
 * what this does: the picture fills the screen, controls appear over it when a
 * key is pressed and leave on their own, and the things the phone's page holds
 * are a row of controls and a panel beside the picture.
 *
 * The ViewModel is the phone's, unchanged. Everything that decides what plays,
 * what next means, when progress is filed and what narration says is in there,
 * and none of it knows which screen is drawing it.
 *
 * # The remote, key by key
 *
 * A video opens playing with nothing over it — pressing a card means "play
 * this", and a row of suggestions rising over the first seconds of it is the
 * thing that was asked not to happen. With the controls away: left and right
 * move a seek target ([SeekDraft]) with the storyboard showing where; OK pauses
 * or plays and brings the controls; up brings them with focus on play, and down
 * brings them with focus on the suggestions. The media keys work whatever is
 * showing. With the controls up, the D-pad moves between them as it does
 * anywhere.
 *
 * Back takes away what is showing, one layer at a time — the panel, then the
 * controls — and only with nothing over the picture does it reach the app,
 * where it closes the player: the close button, because a television has no
 * miniplayer for the video to carry on in. The first build closed the player
 * on the first Back whatever was showing, so somebody looking at the
 * suggestions lost the video they were watching.
 */
@Composable
fun TvWatchScreen(
    viewModel: WatchViewModel,
    mediaBaseUrl: String,
    /** Picked from up next: the same sitting, resumed rather than restarted. */
    onOpenVideo: (String) -> Unit,
    /** Advancing, which starts the next video at its beginning. */
    onAdvanceTo: (String) -> Unit,
    onPlayPrevious: () -> Unit,
    hasPrevious: Boolean,
    onOpenChannel: (String) -> Unit,
    onSaveToPlaylist: (saved: Boolean) -> Unit,
    /** A video in up next, from a long press of OK on its card. */
    onSaveVideo: (Video) -> Unit,
    /** See [TvWatchContent]'s parameter of the same name. */
    onBackStep: (step: () -> Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TvWatchContent(
        state = state,
        player = viewModel.player,
        mediaBaseUrl = mediaBaseUrl,
        hasPrevious = hasPrevious,
        onPlayPause = viewModel::playPause,
        onSeek = viewModel::seekTo,
        onRetry = viewModel::retry,
        onReact = viewModel::react,
        onToggleSaved = {
            onSaveToPlaylist((state as? WatchState.Playing)?.video?.saved == true)
        },
        onToggleNarration = viewModel::toggleNarration,
        onToggleAutoplay = viewModel::toggleAutoplay,
        onSelectSubtitles = viewModel::selectSubtitles,
        // The ViewModel decides what next is — see `WatchViewModel.nextId` —
        // exactly as the phone's screen asks it.
        onPlayNext = {
            val next = (state as? WatchState.Playing)?.nextId.orEmpty()
            if (next.isNotEmpty()) onAdvanceTo(next)
        },
        onPlayPrevious = onPlayPrevious,
        onLoadComments = viewModel::loadComments,
        onOpenVideo = onOpenVideo,
        onOpenChannel = onOpenChannel,
        onSaveVideo = onSaveVideo,
        onBackStep = onBackStep,
    )
}

/** Which control the controls arrive focused on. */
private enum class ControlsEntry { Play, UpNext }

/** What the panel beside the picture is showing, if anything. */
enum class TvPanel { None, Settings, Comments, Description }

@Composable
fun TvWatchContent(
    state: WatchState,
    player: VideoPlayer?,
    mediaBaseUrl: String,
    hasPrevious: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
    onRetry: () -> Unit,
    onReact: (Reaction) -> Unit,
    onToggleSaved: () -> Unit,
    onToggleNarration: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onSelectSubtitles: (String) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onLoadComments: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onSaveVideo: (Video) -> Unit,
    /**
     * Hands the app the player's half of Back: a step that takes away whatever
     * is over the picture and says whether there was anything.
     *
     * The app's one back handler asks it first and closes the player only when
     * it answers false. It was a second `BackHandler` in here, and which of two
     * handlers is asked first turned out not to be a thing to rely on: measured
     * on the television, the same press hid the controls on one run and closed
     * the video on the next. One handler, ordered outward, is the charter's rule
     * for exactly this.
     */
    onBackStep: (step: () -> Boolean) -> Unit,
) {
    val strings = LocalStrings.current

    // A video nobody is touching is still being watched — and on a television
    // the screensaver is what fires over it, which is the same fault with a
    // different picture. Playing rather than open, as on the phone.
    KeepScreenOn((state as? WatchState.Playing)?.playback?.isPlaying == true)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (state) {
            is WatchState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Tokens.text, modifier = Modifier.size(40.dp))
            }

            // A playback error is shown, with a way to try again in place of
            // the controls — the charter's rule, and on a television the button
            // has to be focused or Try again is a word nothing can press.
            is WatchState.Failed -> TvMessage(
                title = strings.couldNotPlay,
                detail = state.message,
                actionLabel = strings.tryAgain,
                onAction = onRetry,
            )

            is WatchState.Upcoming -> TvMessage(
                title = strings.upcomingTitle,
                detail = strings.upcomingDetail,
                actionLabel = "",
                onAction = null,
            )

            is WatchState.Unavailable -> TvMessage(
                title = strings.unavailableTitle,
                detail = unavailableCopy(state.reason, strings),
                actionLabel = "",
                onAction = null,
            )

            is WatchState.Playing -> Playing(
                state = state,
                player = player,
                mediaBaseUrl = mediaBaseUrl,
                strings = strings,
                hasPrevious = hasPrevious,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onRetry = onRetry,
                onReact = onReact,
                onToggleSaved = onToggleSaved,
                onToggleNarration = onToggleNarration,
                onToggleAutoplay = onToggleAutoplay,
                onSelectSubtitles = onSelectSubtitles,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onLoadComments = onLoadComments,
                onOpenVideo = onOpenVideo,
                onOpenChannel = onOpenChannel,
                onSaveVideo = onSaveVideo,
                onBackStep = onBackStep,
            )
        }
    }
}

@Composable
private fun Playing(
    state: WatchState.Playing,
    player: VideoPlayer?,
    mediaBaseUrl: String,
    strings: Strings,
    hasPrevious: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
    onRetry: () -> Unit,
    onReact: (Reaction) -> Unit,
    onToggleSaved: () -> Unit,
    onToggleNarration: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onSelectSubtitles: (String) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onLoadComments: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onSaveVideo: (Video) -> Unit,
    onBackStep: (step: () -> Boolean) -> Unit,
) {
    val playback = state.playback
    val live = state.isLive || playback.isLive
    var controls by remember { mutableStateOf(false) }
    // Where focus goes when the controls come up: play, or the suggestions
    // when it was down that brought them.
    var entry by remember { mutableStateOf(ControlsEntry.Play) }
    var panel by remember { mutableStateOf(TvPanel.None) }
    var draft by remember { mutableStateOf(SeekDraft.None) }
    // Bumped by every key, so the timer below starts again: controls leave
    // once the remote has been put down, not while somebody is reading them.
    var touched by remember { mutableIntStateOf(0) }
    // Whether focus is in here at all. A sheet opened from the controls — the
    // save sheet, from a long press in up next — holds it outside, and the
    // controls stay while it does: hidden under it, the card it was opened
    // from would be gone, and focus would have nowhere to come back to.
    var holdsFocus by remember { mutableStateOf(true) }

    val focusManager = LocalFocusManager.current
    val surfaceFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    // The card up next was left on, for as long as this video plays: Back and
    // Down again land where the remote was, as a Home shelf does. A new video
    // starts at its first card, the one that plays next.
    val upNextMemory = remember(state.video.id) { RowMemory() }
    val panelFocus = remember { FocusRequester() }
    val focusMemory = LocalFocusMemory.current
    // Where focus was as a panel opened — see the effect below.
    var panelMark by remember { mutableStateOf(FocusTrail.None) }

    fun commitDraft() {
        onSeek(draft.targetSeconds)
        draft = SeekDraft.None
    }

    fun showControls(from: ControlsEntry) {
        entry = from
        controls = true
    }

    // A new video starts clear: nothing over it, and whatever panel was open
    // about the last one shut.
    LaunchedEffect(state.video.id) {
        controls = false
        panel = TvPanel.None
        draft = SeekDraft.None
    }

    // A video that has ended with autoplay off has nothing more to show but
    // what to watch next — so it shows that, as down would, rather than leaving
    // a still frame and no hint that the remote has anything to offer. With
    // autoplay on the next video is already on its way, and nothing comes up.
    LaunchedEffect(playback.hasEnded) {
        if (playback.hasEnded && !state.autoplay && !live) {
            showControls(ControlsEntry.UpNext)
        }
    }

    LaunchedEffect(controls, panel, touched, playback.isPlaying, holdsFocus) {
        if (controls && panel == TvPanel.None && playback.isPlaying && holdsFocus) {
            delay(HIDE_AFTER_MILLIS)
            controls = false
        }
    }

    // Focus follows what is showing, once the node being asked has been
    // composed and placed.
    LaunchedEffect(controls, panel, entry) {
        awaitPlaced()
        // A panel closing hands focus back to the button that opened it, not
        // to play: measured, closing the settings panel put focus three
        // buttons away from where the remote had been.
        val reopened = if (panel == TvPanel.None) panelMark else FocusTrail.None
        if (panel == TvPanel.None) panelMark = FocusTrail.None
        runCatching {
            when {
                panel != TvPanel.None -> panelFocus.requestFocus()
                controls && entry == ControlsEntry.UpNext && state.upNext.isNotEmpty() ->
                    upNextMemory.enter(state.upNext.map { it.id })
                controls -> if (!reopened.restore()) playFocus.requestFocus()
                else -> surfaceFocus.requestFocus()
            }
        }
    }

    // The draft is sent once the remote goes quiet — see [SeekDraft].
    LaunchedEffect(draft) {
        if (!draft.active) return@LaunchedEffect
        delay(SEEK_COMMIT_MILLIS)
        commitDraft()
    }

    // Back takes away one layer at a time: the panel, then the controls, and
    // answers false with nothing showing so the app closes the player. The
    // lambda reads the states when it is called, not when it was handed over.
    DisposableEffect(Unit) {
        onBackStep {
            when {
                panel != TvPanel.None -> { panel = TvPanel.None; true }
                controls -> { controls = false; true }
                else -> false
            }
        }
        onDispose { onBackStep { false } }
    }

    fun seekPress(direction: Int) {
        if (live) return
        draft = draft.press(direction, playback.positionSeconds, playback.durationSeconds)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onFocusChanged { holdsFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Only while the controls are up is there a timer to put off;
                // with them away, bumping it would recompose the player on
                // every repeat of a held key for nothing.
                if (controls) touched++
                when {
                    handleMediaKey(event, playback, onPlayPause, ::seekPress, onPlayNext, onPlayPrevious) -> true
                    // Everything else is the focused control's while the
                    // controls or a panel are up.
                    controls || panel != TvPanel.None -> false
                    else -> when (event.key) {
                        Key.DirectionLeft -> { seekPress(-1); true }
                        Key.DirectionRight -> { seekPress(+1); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            // OK during a run sends it now rather than pausing:
                            // somebody who has found the place presses OK to
                            // say "there".
                            if (draft.active) commitDraft() else onPlayPause()
                            showControls(ControlsEntry.Play)
                            true
                        }
                        Key.DirectionUp -> { showControls(ControlsEntry.Play); true }
                        Key.DirectionDown -> { showControls(ControlsEntry.UpNext); true }
                        else -> false
                    }
                }
            }
            // Up with nothing above the focused control — the seek bar, or the
            // row under a broadcast's badge — takes the controls away, as Back
            // does. It found nothing to move to and stopped there: reported as
            // up next staying on screen however many times up was pressed.
            // Here, after the focused control has had the key and before the
            // root moves focus with it.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) return@onKeyEvent false
                if (!controls || panel != TvPanel.None) return@onKeyEvent false
                if (!focusManager.moveFocus(FocusDirection.Up)) controls = false
                true
            },
    ) {
        if (player != null) {
            VideoSurface(player = player, modifier = Modifier.fillMaxSize())
        }

        // Where focus rests while nothing is drawn over the picture.
        //
        // A remote's keys go to whatever has focus, and with the controls away
        // nothing on screen is focusable. Without this the first press after
        // they fade would go nowhere at all. It cannot be focused while the
        // controls are up, so moving down through them can never land on an
        // invisible box the size of the screen.
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(surfaceFocus)
                .focusProperties { canFocus = !controls && panel == TvPanel.None }
                .focusable(),
        )

        // Not under the controls: up next fills the bottom of the screen where
        // the line is drawn, and through its scrim the words read as part of
        // the cards. Back as soon as the controls go.
        if (!controls) {
            SubtitleOverlay(
                cues = state.subtitleCues,
                positionSeconds = playback.positionSeconds,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TV_SAFE_VERTICAL + Space.xl),
            )
        }

        if (playback.isBuffering) {
            CircularProgressIndicator(
                color = Tokens.text,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }

        if (playback.error.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))) {
                TvMessage(
                    title = strings.couldNotPlay,
                    detail = playback.error,
                    actionLabel = strings.tryAgain,
                    onAction = onRetry,
                )
            }
            return@Box
        }

        // The seek bar alone, while a run of presses is going with the
        // controls away: the place being chosen, and nothing else in the way.
        AnimatedVisibility(
            visible = draft.active && !controls,
            enter = fadeIn(tween(FADE_MILLIS)),
            exit = fadeOut(tween(FADE_MILLIS)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(BOTTOM_SCRIM)
                    .padding(horizontal = TV_SAFE_HORIZONTAL, vertical = TV_SAFE_VERTICAL),
            ) {
                SeekBar(
                    playback = playback,
                    draft = draft,
                    storyboard = state.storyboard,
                    mediaBaseUrl = mediaBaseUrl,
                    focusable = false,
                    onPress = ::seekPress,
                )
            }
        }

        // Composed for as long as the player is, and faded rather than added.
        // Inside `AnimatedVisibility` the whole block — title, buttons, seek
        // bar, up next — was built on the press that showed it: traced on the
        // household's Chromecast, one 218ms frame (70ms composing, 103ms
        // measuring, 43ms drawing), and the picture froze for it. Built with
        // the player instead, the cost falls behind the loading spinner.
        //
        // Hidden, it is neither seen nor reachable: alpha 0 is not drawn, its
        // semantics are cleared (a switch-access user is not offered buttons
        // that are not there), and focus cannot enter it — the D-pad is the
        // player's own while the controls are away, and this says so again.
        val controlsAlpha by animateFloatAsState(
            targetValue = if (controls) 1f else 0f,
            animationSpec = tween(FADE_MILLIS),
            label = "controls",
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = controlsAlpha }
                .then(if (controls) Modifier else Modifier.clearAndSetSemantics {})
                .focusProperties { onEnter = { if (!controls) cancelFocusChange() } }
                .focusGroup(),
        ) {
            Controls(
                state = state,
                live = live,
                draft = draft,
                mediaBaseUrl = mediaBaseUrl,
                strings = strings,
                hasPrevious = hasPrevious,
                playFocus = playFocus,
                upNextMemory = upNextMemory,
                onPlayPause = onPlayPause,
                onSeekPress = ::seekPress,
                onReact = onReact,
                onToggleSaved = onToggleSaved,
                onToggleNarration = onToggleNarration,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onOpenPanel = { opened ->
                    panelMark = focusMemory?.mark() ?: FocusTrail.None
                    if (opened == TvPanel.Comments) onLoadComments()
                    panel = opened
                },
                onOpenVideo = onOpenVideo,
                onOpenChannel = onOpenChannel,
                onSaveVideo = onSaveVideo,
            )
        }

        AnimatedVisibility(
            visible = panel != TvPanel.None,
            enter = slideInHorizontally(tween(FADE_MILLIS)) { it } + fadeIn(tween(FADE_MILLIS)),
            exit = slideOutHorizontally(tween(FADE_MILLIS)) { it } + fadeOut(tween(FADE_MILLIS)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            // The facts each panel reads rather than the whole state, which is
            // a new object on every tick of the playhead: handed that, an open
            // panel re-registered every comment four times a second.
            SidePanel(
                panel = panel,
                video = state.video,
                subtitleLanguage = state.subtitleLanguage,
                canNarrate = state.canNarrate,
                narrating = state.narrating,
                autoplay = state.autoplay,
                comments = state.comments,
                loadingComments = state.loadingComments,
                commentsLoaded = state.commentsLoaded,
                strings = strings,
                focus = panelFocus,
                onSelectSubtitles = onSelectSubtitles,
                onToggleNarration = onToggleNarration,
                onToggleAutoplay = onToggleAutoplay,
            )
        }
    }
}

/**
 * The remote's media keys, which work whatever is on screen.
 *
 * Play and pause are told apart from the toggle: a remote with separate keys
 * means them separately, and a pause key that resumed a paused video would be
 * the one key on it that lies.
 */
private fun handleMediaKey(
    event: KeyEvent,
    playback: PlaybackState,
    onPlayPause: () -> Unit,
    onSeekPress: (Int) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
): Boolean {
    when (event.key) {
        Key.MediaPlayPause -> onPlayPause()
        Key.MediaPlay -> if (!playback.isPlaying) onPlayPause()
        Key.MediaPause -> if (playback.isPlaying) onPlayPause()
        Key.MediaFastForward -> onSeekPress(+1)
        Key.MediaRewind -> onSeekPress(-1)
        Key.MediaNext -> onPlayNext()
        Key.MediaPrevious -> onPlayPrevious()
        else -> return false
    }
    return true
}

@Composable
private fun Controls(
    state: WatchState.Playing,
    live: Boolean,
    draft: SeekDraft,
    mediaBaseUrl: String,
    strings: Strings,
    hasPrevious: Boolean,
    playFocus: FocusRequester,
    upNextMemory: RowMemory,
    onPlayPause: () -> Unit,
    onSeekPress: (Int) -> Unit,
    onReact: (Reaction) -> Unit,
    onToggleSaved: () -> Unit,
    onToggleNarration: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onOpenPanel: (TvPanel) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onSaveVideo: (Video) -> Unit,
) {
    val video = state.video
    val playback = state.playback
    // The name of whichever control is focused, said once under the row: a
    // glyph alone is a guess from across a room, and a label under each of ten
    // glyphs is a second row of text nobody reads.
    var focusedLabel by remember { mutableStateOf("") }

    // A column, with the gap between title and controls the part that gives,
    // and the title after it. The two were aligned to opposite ends of one
    // box, and once the suggestions were up the bottom block grew into the
    // title: the channel's name was drawn through the seek bar.
    //
    // The title gives, not the bottom block: weighted, it is measured after
    // the bottom block and gets what is left. Last in an unweighted column,
    // the bottom block was the one cut short, and up next at the end of it —
    // a two-line title over the controls left its titles one line each, and
    // a seek's preview pushed the row off the screen.
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .background(TOP_SCRIM)
                .padding(horizontal = TV_SAFE_HORIZONTAL, vertical = TV_SAFE_VERTICAL),
        ) {
            Text(
                text = video.title,
                color = Tokens.text,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = video.channel.name,
                color = Tokens.text2,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 1,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(BOTTOM_SCRIM)
                .padding(top = Space.md, bottom = TV_SAFE_VERTICAL),
        ) {
            Column(Modifier.padding(horizontal = TV_SAFE_HORIZONTAL)) {
                if (live) {
                    LiveBadge(strings.live)
                } else {
                    SeekBar(
                        playback = playback,
                        draft = draft,
                        storyboard = state.storyboard,
                        mediaBaseUrl = mediaBaseUrl,
                        focusable = true,
                        onPress = onSeekPress,
                        onFocused = { focusedLabel = "" },
                    )
                }
                Spacer(Modifier.height(Space.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    // Previous and next keep their places whether or not there
                    // is anywhere to go, faint and not pressable, as on the
                    // phone — so the play button never moves.
                    TvControl(
                        icon = PreviousIcon,
                        label = strings.playPrevious,
                        enabled = hasPrevious,
                        onFocused = { focusedLabel = it },
                        onClick = onPlayPrevious,
                    )
                    TvControl(
                        icon = if (playback.isPlaying) PauseIcon else PlayIcon,
                        label = if (playback.isPlaying) strings.pause else strings.play,
                        onFocused = { focusedLabel = it },
                        onClick = onPlayPause,
                        size = PLAY_SIZE,
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    TvControl(
                        icon = NextIcon,
                        label = strings.playNext,
                        enabled = state.nextId.isNotEmpty(),
                        onFocused = { focusedLabel = it },
                        onClick = onPlayNext,
                    )
                    Spacer(Modifier.width(Space.xl))
                    TvControl(
                        icon = CaptionsIcon,
                        label = strings.settingsInPlayer,
                        onFocused = { focusedLabel = it },
                        onClick = { onOpenPanel(TvPanel.Settings) },
                    )
                    // Narration on and off from the row, beside the settings it
                    // also lives in: the household's most-reached-for switch,
                    // which was a panel and a row deep. Offered on the panel's
                    // rule — a recorded video with captions to read.
                    if (state.canNarrate) {
                        TvControl(
                            icon = if (state.narrating) NarrationFilledIcon else NarrationIcon,
                            label = strings.narration,
                            selected = state.narrating,
                            onFocused = { focusedLabel = it },
                            onClick = onToggleNarration,
                        )
                    }
                    TvControl(
                        icon = if (video.reaction == Reaction.Like) ThumbFilledIcon else ThumbIcon,
                        label = strings.like,
                        selected = video.reaction == Reaction.Like,
                        onFocused = { focusedLabel = it },
                        onClick = { onReact(Reaction.Like) },
                    )
                    TvControl(
                        icon = if (video.saved) SaveFilledIcon else SaveIcon,
                        label = strings.saveToPlaylist,
                        selected = video.saved,
                        onFocused = { focusedLabel = it },
                        onClick = onToggleSaved,
                    )
                    TvControl(
                        icon = CommentsIcon,
                        label = strings.comments,
                        onFocused = { focusedLabel = it },
                        onClick = { onOpenPanel(TvPanel.Comments) },
                    )
                    TvControl(
                        icon = DescriptionIcon,
                        label = strings.description,
                        onFocused = { focusedLabel = it },
                        onClick = { onOpenPanel(TvPanel.Description) },
                    )
                    ChannelControl(
                        channel = video.channel,
                        mediaBaseUrl = mediaBaseUrl,
                        onFocused = { focusedLabel = it },
                        onClick = { onOpenChannel(video.channel.id) },
                    )
                }
                // Held at one line's height whether or not anything is named,
                // so the up next row below does not jump as focus moves.
                Text(
                    text = focusedLabel,
                    color = Tokens.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = Space.sm).height(20.dp),
                )
            }

            // Its own composable, handed the list rather than the state: the
            // controls follow the playhead four times a second, and the list
            // is the same object on every tick, so the row skips them all.
            if (state.upNext.isNotEmpty()) {
                UpNextRow(
                    upNext = state.upNext,
                    mediaBaseUrl = mediaBaseUrl,
                    strings = strings,
                    memory = upNextMemory,
                    onFocused = { focusedLabel = "" },
                    onOpenVideo = onOpenVideo,
                    onSaveVideo = onSaveVideo,
                )
            }
        }
    }
}

/**
 * The suggestions under the controls, entered at the card they were left on —
 * the one that plays next, until the remote has moved along them.
 */
@Composable
private fun UpNextRow(
    upNext: List<Video>,
    mediaBaseUrl: String,
    strings: Strings,
    memory: RowMemory,
    onFocused: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onSaveVideo: (Video) -> Unit,
) {
    Text(
        text = strings.upNext,
        color = Tokens.text,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = TV_SAFE_HORIZONTAL, top = Space.sm),
    )
    val current by rememberUpdatedState(upNext)
    // Its scroll is the memory's, kept while the controls are away, so the
    // card focus comes back to is still composed. It used to go back to the
    // start each time they went: reported as Back and Down after the fifth
    // video landing on the first.
    LazyRow(
        state = memory.scroll,
        // Room above for a focused card's rise. None below: the rise is the
        // picture's, and under the picture is its title — the 8dp that were
        // here were what a two-line title over the controls cost the titles
        // in this row their second line.
        contentPadding = PaddingValues(
            start = TV_SAFE_HORIZONTAL,
            end = TV_SAFE_HORIZONTAL,
            top = FOCUS_MAX_OUTSET / 2,
        ),
        // Down from any control lands on the card the row was left on, not
        // on the one under the control.
        modifier = Modifier.entersAt(
            state = memory.scroll,
            index = { rowEntryIndex(current.map { it.id }, memory.lastId, memory.lastIndex) },
            requester = { memory.cards[current.getOrNull(it)?.id] },
        ),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        itemsIndexed(upNext, key = { _, next -> next.id }) { index, next ->
            val requester = remember { FocusRequester() }
            DisposableEffect(next.id) {
                memory.cards[next.id] = requester
                onDispose { if (memory.cards[next.id] === requester) memory.cards.remove(next.id) }
            }
            Box(
                Modifier
                    .focusRequester(requester)
                    .onFocusChanged {
                        if (it.hasFocus) {
                            memory.focused(index, next.id)
                            onFocused()
                        }
                    },
            ) {
                TvVideoCard(
                    video = next,
                    mediaBaseUrl = mediaBaseUrl,
                    strings = strings,
                    onClick = { onOpenVideo(next.id) },
                    withMeta = false,
                    opening = false,
                    // Remembered: a new list each time is a card that can
                    // never skip.
                    menu = remember(next, strings) { videoMenu(strings, onSave = { onSaveVideo(next) }) },
                    // Smaller than a shelf's: the row shares the
                    // screen with the controls above it.
                    width = UP_NEXT_CARD_WIDTH,
                )
            }
        }
    }
}

/**
 * The bar across the bottom, and the place a run of presses has reached.
 *
 * Focusable while the controls are up, so left and right on it move the target
 * as they do with the controls away; drawn but not focusable during a run with
 * the controls away, where the screen itself is taking the keys.
 */
@Composable
private fun SeekBar(
    playback: PlaybackState,
    draft: SeekDraft,
    storyboard: StoryboardState,
    mediaBaseUrl: String,
    focusable: Boolean,
    onPress: (Int) -> Unit,
    onFocused: () -> Unit = {},
) {
    val duration = playback.durationSeconds
    val shown = if (draft.active) draft.targetSeconds else playback.positionSeconds
    val fraction = if (duration > 0.0) (shown / duration).toFloat().coerceIn(0f, 1f) else 0f
    var focused by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (draft.active) {
                // Over the place, not over the playhead: the still is what the
                // target looks like, and it travels with it.
                val previewLeft = (maxWidth - PREVIEW_WIDTH) * fraction
                ScrubPreview(
                    storyboard = storyboard,
                    seconds = draft.targetSeconds,
                    mediaBaseUrl = mediaBaseUrl,
                    fill = false,
                    modifier = Modifier
                        .offset(x = previewLeft)
                        .padding(bottom = Space.sm)
                        .size(PREVIEW_WIDTH, PREVIEW_WIDTH * 9f / 16f)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(SEEK_TARGET)
                .then(
                    if (focusable) {
                        Modifier
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) onFocused()
                            }
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> { onPress(-1); true }
                                    Key.DirectionRight -> { onPress(+1); true }
                                    else -> false
                                }
                            }
                            .focusStop()
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            val track = if (focused) TRACK_FOCUSED else TRACK
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(track)
                    .clip(RoundedCornerShape(track / 2))
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Tokens.brand),
                )
            }
        }
        Text(
            text = "${formatDuration(shown.toInt())} / ${formatDuration(duration.toInt())}",
            color = Tokens.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun LiveBadge(label: String) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Tokens.brand)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * One control in the row over the picture: a glyph on a disc of glass painted
 * from black, because it sits over video Compose cannot sample.
 *
 * On or off is the glyph's fill and the disc's inversion together — the
 * charter's "a state is a change of kind".
 */
@Composable
private fun TvControl(
    icon: ImageVector,
    label: String,
    onFocused: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = CONTROL_SIZE,
) {
    // The label is read while focused, not captured as focus arrives: play
    // turns into pause under a focused control, and the first build went on
    // naming the button "Play" over a pause glyph.
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, label) { if (focused) onFocused(label) }
    Box(
        modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            // On is the design system's selected control, the inverted surface;
            // off is glass painted from black, over the video.
            .pressableGlassSurface(CircleShape, selected = selected, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                selected -> Tokens.invertText
                enabled -> Tokens.text
                else -> Tokens.text.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** The channel's own picture as the control that opens it. */
@Composable
private fun ChannelControl(
    channel: Channel,
    mediaBaseUrl: String,
    onFocused: (String) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(CONTROL_SIZE)
            .onFocusChanged { if (it.isFocused) onFocused(channel.name) }
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChannelAvatar(
            name = channel.name,
            mediaBaseUrl = mediaBaseUrl,
            path = channel.avatarPath,
            size = CONTROL_SIZE - 8.dp,
        )
    }
}

/**
 * The panel beside the picture: the player's settings, the comments, or the
 * description — the three things the phone's page holds under its picture.
 *
 * Painted, not sampled: it opens over moving video, which nothing here can
 * blur on a television's GPU at sixty frames.
 */
@Composable
private fun SidePanel(
    panel: TvPanel,
    video: Video,
    subtitleLanguage: String,
    canNarrate: Boolean,
    narrating: Boolean,
    autoplay: Boolean,
    comments: List<Comment>,
    loadingComments: Boolean,
    commentsLoaded: Boolean,
    strings: Strings,
    focus: FocusRequester,
    onSelectSubtitles: (String) -> Unit,
    onToggleNarration: () -> Unit,
    onToggleAutoplay: () -> Unit,
) {
    // Split once per description, not on every recomposition of the panel.
    val paragraphs = remember(video.description) { paragraphsOf(video.description) }
    Box(
        Modifier
            .fillMaxHeight()
            .width(PANEL_WIDTH)
            .background(Tokens.bg.copy(alpha = 0.94f)),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.xl,
                end = TV_SAFE_HORIZONTAL,
                top = TV_SAFE_VERTICAL,
                bottom = TV_SAFE_VERTICAL,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            when (panel) {
                TvPanel.None -> Unit
                TvPanel.Settings -> settingsRows(
                    video.subtitles, subtitleLanguage, canNarrate, narrating, autoplay,
                    strings, focus, onSelectSubtitles, onToggleNarration, onToggleAutoplay,
                )
                TvPanel.Comments -> commentRows(comments, loadingComments, commentsLoaded, strings, focus)
                TvPanel.Description -> descriptionRows(video, paragraphs, strings, focus)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.panelTitle(text: String) {
    item(key = "title") {
        Text(
            text = text,
            color = Tokens.text,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = Space.sm),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsRows(
    subtitles: List<SubtitleTrack>,
    subtitleLanguage: String,
    canNarrate: Boolean,
    narrating: Boolean,
    autoplay: Boolean,
    strings: Strings,
    focus: FocusRequester,
    onSelectSubtitles: (String) -> Unit,
    onToggleNarration: () -> Unit,
    onToggleAutoplay: () -> Unit,
) {
    panelTitle(strings.settingsInPlayer)
    item(key = "subtitles-heading") { PanelHeading(strings.subtitles) }
    // Off first, and it is where focus lands: the commonest reason to open this
    // is to turn captions on or off, and Off is the one row every video has.
    item(key = "subtitles-off") {
        PanelRow(
            label = strings.off,
            selected = subtitleLanguage.isEmpty(),
            onClick = { if (subtitleLanguage.isNotEmpty()) onSelectSubtitles(subtitleLanguage) },
            modifier = Modifier.focusRequester(focus),
        )
    }
    items(subtitles, key = { "subtitles-${it.language}" }) { track ->
        PanelRow(
            label = trackLabel(track, strings),
            selected = subtitleLanguage == track.language,
            onClick = { onSelectSubtitles(track.language) },
        )
    }
    // Absent rather than disabled where there is no track to read, as on the
    // phone's panel and for its reason.
    if (canNarrate) {
        item(key = "narration") {
            PanelRow(label = strings.narration, selected = narrating, onClick = onToggleNarration)
        }
    }
    item(key = "autoplay") {
        PanelRow(label = strings.autoplay, selected = autoplay, onClick = onToggleAutoplay)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.commentRows(
    comments: List<Comment>,
    loadingComments: Boolean,
    commentsLoaded: Boolean,
    strings: Strings,
    focus: FocusRequester,
) {
    panelTitle(strings.comments)
    when {
        // Still asking is not "none": a spinner, focused so Back and the keys
        // still have somewhere to be.
        loadingComments || !commentsLoaded -> item(key = "loading") {
            Box(
                Modifier.fillMaxWidth().padding(Space.xl).focusRequester(focus).focusStop(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Tokens.text, modifier = Modifier.size(28.dp))
            }
        }

        comments.isEmpty() -> item(key = "empty") {
            Text(
                text = strings.noComments,
                color = Tokens.text2,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.focusRequester(focus).focusStop().padding(Space.sm),
            )
        }

        else -> comments.forEachIndexed { index, comment ->
            item(key = comment.id) {
                CommentBlock(
                    comment = comment,
                    modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                )
            }
            items(comment.replies, key = { it.id }) { reply ->
                CommentBlock(comment = reply, modifier = Modifier.padding(start = Space.xl))
            }
        }
    }
}

@Composable
private fun CommentBlock(comment: Comment, modifier: Modifier = Modifier) {
    // A stop for the remote, not a control: see `focusStop`.
    Column(modifier.fillMaxWidth().focusStop().padding(Space.sm)) {
        Text(
            text = comment.authorHandle,
            color = Tokens.text2,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 1,
        )
        Text(
            text = comment.text,
            color = Tokens.text,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.descriptionRows(
    video: Video,
    paragraphs: List<String>,
    strings: Strings,
    focus: FocusRequester,
) {
    panelTitle(strings.description)
    item(key = "meta") {
        Text(
            // The description box's own rule: no count of zero, no empty date.
            text = listOfNotNull(
                if (video.viewCount > 0) formatViews(video.viewCount, strings) else null,
                if (video.hasPublishedDate) formatDate(video.publishedAt, strings) else null,
            ).joinToString(" · "),
            color = Tokens.text2,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.focusRequester(focus).focusStop().padding(Space.sm),
        )
    }
    // A paragraph to a stop, so the remote reads down a long description a
    // screen at a time instead of not at all.
    itemsIndexed(paragraphs, key = { index, _ -> "p$index" }) { _, text ->
        Text(
            text = text,
            color = Tokens.text,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.fillMaxWidth().focusStop().padding(Space.sm),
        )
    }
}

/** A description split into its paragraphs, blank ones dropped. */
private fun paragraphsOf(text: String): List<String> =
    text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun PanelHeading(text: String) {
    Text(
        text = text,
        color = Tokens.text2,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = Space.sm, start = Space.sm),
    )
}

/** A row in the panel; on is the inverted surface, with the ink following it. */
@Composable
private fun PanelRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(PANEL_ROW)
            // The phone's track chips' material, so on and off look the same
            // in both players.
            .pressableGlassControl(RoundedCornerShape(percent = 50), selected = selected, onClick = onClick)
            .padding(horizontal = Space.lg),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (selected) Tokens.invertText else Tokens.text,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How long the controls stay up once the remote is put down. YouTube's TV app: about five. */
private const val HIDE_AFTER_MILLIS = 5_000L

private const val FADE_MILLIS = 200

private val CONTROL_SIZE = 48.dp
private val PLAY_SIZE = 56.dp
private val PANEL_WIDTH = 380.dp
private val PANEL_ROW = 48.dp
private val PREVIEW_WIDTH = 224.dp
private val UP_NEXT_CARD_WIDTH = 220.dp

/** The bar's focusable height — a target for focus to sit on, not a finger. */
private val SEEK_TARGET = 24.dp
private val TRACK = 4.dp

/** Thicker while focused, so it says "left and right move this". */
private val TRACK_FOCUSED = 8.dp

private val TOP_SCRIM = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.75f),
    1f to Color.Transparent,
)
private val BOTTOM_SCRIM = Brush.verticalGradient(
    0f to Color.Transparent,
    0.35f to Color.Black.copy(alpha = 0.6f),
    1f to Color.Black.copy(alpha = 0.85f),
)

// --- previews ---------------------------------------------------------------

private val previewVideo = Video(
    id = "v",
    title = "A long walk through the old quarter at dawn",
    channel = Channel(id = "c", name = "Slow Travel", handle = "", avatarPath = ""),
    durationSeconds = 1_820,
    viewCount = 48_200,
    publishedAt = "2026-10-12T08:00:00Z",
    thumbnailPath = "",
    description = "The first paragraph.\n\nThe second, which is longer and says more.",
)

private val previewPlaying = WatchState.Playing(
    video = previewVideo,
    playback = PlaybackState(isPlaying = true, positionSeconds = 412.0, durationSeconds = 1_820.0),
    upNext = listOf(previewVideo.copy(id = "n1"), previewVideo.copy(id = "n2")),
)

@Composable
private fun preview(state: WatchState, vietnamese: Boolean = false) = MytubeTheme {
    CompositionLocalProvider(
        LocalTelevision provides true,
        LocalStrings provides if (vietnamese) VietnameseStrings else LocalStrings.current,
    ) {
        TvWatchContent(state, null, "", false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvWatchPlayingPreview() = preview(previewPlaying)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvWatchLoadingPreview() = preview(WatchState.Loading)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvWatchFailedPreview() = preview(WatchState.Failed("gateway answered 502"))

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvWatchVietnamesePreview() = preview(previewPlaying, vietnamese = true)
