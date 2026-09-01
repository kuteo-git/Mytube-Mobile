package com.mytube.app.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.theme.Tokens
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A bottom sheet made of the same glass as the bars.
 *
 * # Why this is written by hand instead of `ModalBottomSheet`
 *
 * Because Material's sheet **cannot** be frosted here, and a file used to exist
 * saying so — `SheetBackdrop.kt`, now folded into this comment so the knowledge
 * survives the thing it was explaining.
 *
 * `ModalBottomSheet` renders into its own popup layer with its own coordinate
 * space. Haze positions its effect from the node's `positionInRoot`, which
 * inside that layer starts at zero — so the effect drew the slice of the app
 * from the *top of the screen* into the sheet's place. On the watch screen that
 * is the video: the picture appeared blurred in its own frame while the sheet's
 * rows floated transparent over the comments underneath. That is not a tuning
 * problem and no parameter fixes it; **the sheet is not in the scene Haze
 * recorded.** So this one is in the scene — an ordinary child of the caller's
 * full-screen `Box`, at real coordinates, which is the whole difference.
 *
 * The cost is that everything the platform sheet gave away has to be built:
 * the rise, tap-outside, drag-down, and back. All four are here, because a sheet
 * missing any one of them is a trap rather than a sheet.
 *
 * # Why the back handler is local, against the rule
 *
 * `App.kt` holds one `BackHandler` for the whole app, deliberately: back is a
 * question about the navigation state and each screen knows only its own part.
 * A sheet is the exception that proves it. Whether one is open is not navigation
 * — `settingsOpen` is a `remember` inside `WatchScreen` and does not exist
 * anywhere the central handler can see it — and `ModalBottomSheet` handled its
 * own back for the same reason. The innermost enabled handler wins, so an open
 * sheet takes the gesture and the video underneath is left alone.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.GlassSheet(
    visible: Boolean,
    /**
     * What to blur, or null to fall back to the wash.
     *
     * Passed rather than read from [LocalHaze] because the two sheets blur two
     * different scenes: the profile sheet opens over the shell's tab content,
     * which is what the shell registers; the player's opens over the watch page,
     * which is a sibling of the shell and registers its own. A sheet that read
     * the ambient one would frost the feed hiding behind the video.
     */
    backdrop: LayerBackdrop?,
    /**
     * What sits between the sheet and the app.
     *
     * A parameter, not a constant: the player's settings are about the video
     * playing above them, and dimming that video is dimming the thing being
     * adjusted — so that one passes [Color.Transparent]. The profile sheet
     * covers a feed it has no relationship with and dims it like any modal.
     * Same material, different answer to what is behind.
     */
    scrim: Color,
    onDismiss: () -> Unit,
    /**
     * How much of a portrait screen this sheet may take.
     *
     * A parameter because the two sheets hold different things: the player's is
     * two switches and a row of chips over a video that must stay the subject,
     * and the save sheet is a *list* somebody scans — at the player's share it
     * showed three rows of a dozen.
     */
    maxHeightFraction: Float = MAX_HEIGHT_FRACTION,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Driven by a transition state rather than by `visible` directly, so both
    // halves of the movement actually play. An `AnimatedVisibility` composed
    // already visible has nothing to animate *from* — the sheet would appear
    // whole and instantly — and one removed by an `if` above it has nothing to
    // animate *to*, so it would vanish rather than slide away. The composable
    // stays until the exit has finished, which is what the second half of this
    // condition waits for.
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    if (!shown.targetState && !shown.currentState) return

    BackHandler(visible) { onDismiss() }

    // The scrim, and the tap target that closes on a press outside.
    //
    // `indication = null`: a ripple spreading across the whole screen from
    // wherever a finger landed is not what dismissing looks like anywhere.
    AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
    }

    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var sheetHeight by remember { mutableStateOf(0f) }
    // A fraction of whatever the app is drawn into, so the video or the feed the
    // sheet opens over is still the larger part of the screen.
    //
    // **Two fractions, because a landscape phone has no height to give.** 45% of
    // a portrait screen is about 380dp and holds this panel comfortably; 45% of
    // a phone turned sideways is about 175dp, and the settings sheet came up
    // with most of itself below the fold — reported from fullscreen, where the
    // player is the one place this app ever *is* landscape. The thing being
    // protected is what is behind the sheet, and behind it there is a video
    // whose subject is in the middle of the frame either way.
    val container = LocalWindowInfo.current.containerSize
    val fraction =
        if (container.width > container.height) LANDSCAPE_FRACTION else maxHeightFraction
    val maxHeight = with(LocalDensity.current) { (container.height * fraction).toDp() }

    // Reset each time it opens. The `Animatable` lives in the caller's
    // composition, so a sheet dragged shut and reopened would otherwise come
    // back sitting exactly where it was let go of.
    LaunchedEffect(visible) { if (visible) offset.snapTo(0f) }

    // Under the cap the sheet no longer fills the width, so it needs a full-width
    // row to be centred in — `AnimatedVisibility` sizes itself to its content.
    AnimatedVisibility(
        visibleState = shown,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
      Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                // Bounded on both axes, and they are two different problems.
                //
                // **Width**, because a landscape phone is 844dp across and this
                // sheet holds two switches and a row of chips. Spanning that is
                // a band of empty glass with a control at each end, which reads
                // as the sheet having been stretched rather than laid out. A
                // portrait phone is under this cap, so nothing changes there.
                //
                // **Height**, because landscape is only 390dp tall and content
                // sized for portrait then covers two thirds of the screen — the
                // video these settings are about. `fillMaxWidth` and a wrapped
                // height are correct in portrait and wrong the moment the phone
                // turns, which is exactly how it was reported.
                .widthIn(max = MAX_WIDTH)
                .heightIn(max = maxHeight)
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .onSizeChanged { sheetHeight = it.height.toFloat() }
                .clip(GlassRadius.sheet),
        ) {
            // The glass, sized by the sheet rather than sizing it — the same
            // `matchParentSize` rule [BarBackdrop] records: a backdrop that
            // applies `fillMaxSize` inside is measured against the incoming
            // constraints and grows to the whole screen.
            GlassBackdrop(
                Modifier.matchParentSize(),
                backdrop,
                fromTop = true,
                shape = GlassRadius.sheet,
                tint = TINT_MODAL,
            )

            Column(Modifier.fillMaxWidth()) {
                // The drag handle, and the only place the drag is read.
                //
                // Not the whole sheet: the panel below holds a horizontally
                // scrolling row of subtitle chips and a switch, and a vertical
                // detector over all of it competes with every one of them.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(sheetHeight) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (offset.value >= sheetHeight * DISMISS_FRACTION) {
                                        onDismiss()
                                    } else {
                                        scope.launch { offset.animateTo(0f) }
                                    }
                                },
                                onDragCancel = { scope.launch { offset.animateTo(0f) } },
                                onVerticalDrag = { _, delta ->
                                    // Down only. Dragging a bottom sheet upward
                                    // has nowhere to go, and letting it move
                                    // makes it look detachable.
                                    scope.launch {
                                        offset.snapTo(
                                            (offset.value + delta).coerceAtLeast(0f),
                                        )
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Tokens.text2),
                    )
                }

                // Scrolls only when the cap bites. A video with six subtitle
                // tracks in landscape is the case that overflows; in portrait
                // nothing here has ever been tall enough to move, and a scroll
                // container that never scrolls costs nothing.
                //
                // Below the handle, deliberately: the handle owns the vertical
                // drag that dismisses, and a scrolling container over it would
                // take that gesture first.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    content()
                }
            }
        }
      }
    }
}

/**
 * How far down the sheet has to be dragged before letting go closes it.
 *
 * A third, and distance only — the same shape of rule as the drag that puts the
 * video away, and its own constant rather than an import from `ui.watch`: the
 * shell does not depend on a screen. A fraction rather than a number of pixels
 * because a thumb's idea of "most of the way down" scales with the sheet, and
 * these two sheets are not the same height.
 */
private const val DISMISS_FRACTION = 0.33f

/**
 * How wide the sheet is allowed to be.
 *
 * Wider than any phone in portrait, so that orientation is untouched; narrower
 * than one in landscape, which is the case this exists for. A number rather than
 * a fraction because what it is protecting is a *reading width* — a row of two
 * words and a switch does not become easier to use at 844dp.
 */
private val MAX_WIDTH = 480.dp

/** How much of a portrait screen the sheet may take. */
private const val MAX_HEIGHT_FRACTION = 0.45f

/** And of a landscape one, where the same content needs a bigger share. */
private const val LANDSCAPE_FRACTION = 0.8f
