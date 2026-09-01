package com.mytube.app.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.theme.Tokens

/**
 * A question in the middle of the screen, made of the same glass as the bars.
 *
 * # Why this is not `AlertDialog`
 *
 * The same reason [GlassSheet] is not `ModalBottomSheet`, and it is worth
 * restating because the mistake is easy to make twice: Material's dialog renders
 * into its own **popup layer** with its own coordinate space, where a sampled
 * backdrop reads the slice of the app from the top of the screen rather than
 * what is behind the pane. `SheetBackdrop.kt` recorded that fault for sheets and
 * drew the wrong conclusion from it — *"a sheet is not made of glass"* — when
 * the right one was *"then do not use a popup layer"*. This is an ordinary child
 * of the caller's full-screen `Box`, at real coordinates, so it can sample.
 *
 * Everything the platform dialog would have given away is therefore rebuilt: the
 * scrim, tap-outside, back, and the enter and exit animations.
 *
 * # It centres above the keyboard, not behind it
 *
 * `imePadding` on the centring box rather than on the pane: what has to move is
 * the *space the pane is centred in*. Padding the pane itself would keep it
 * centred on the whole screen and merely push it up at the end, which on a short
 * phone leaves a field under the keyboard — the one thing an alert with a text
 * field must never do.
 *
 * # Why it is always composed
 *
 * [MutableTransitionState], for [GlassSheet]'s reason: an `if` around this
 * removes the node the exit animation would play on, so the alert would vanish
 * rather than fade, and one composed already visible has nothing to animate
 * *from*.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.GlassAlert(
    visible: Boolean,
    title: String,
    backdrop: LayerBackdrop?,
    onDismiss: () -> Unit,
    /** The buttons, laid out in a row at the bottom: cancel first, the act last. */
    buttons: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    if (!shown.targetState && !shown.currentState) return

    BackHandler(visible) { onDismiss() }

    AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SCRIM)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
    }

    // The keyboard's inset is consumed *here*, so the pane is centred in what is
    // left of the screen rather than in the whole of it.
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visibleState = shown,
            // A little smaller on the way in, which is what a question arriving
            // in the middle of a screen does everywhere. Not a slide: there is
            // no edge it came from.
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            Box(
                Modifier
                    .padding(horizontal = Space.xl)
                    .widthIn(max = MAX_WIDTH)
                    .clip(GlassRadius.panel),
            ) {
                GlassBackdrop(
                    Modifier.matchParentSize(),
                    backdrop,
                    fromTop = true,
                    shape = GlassRadius.panel,
                    tint = TINT_MODAL,
                )

                Column(Modifier.padding(Space.lg)) {
                    Text(
                        text = title,
                        color = Tokens.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Space.md))
                    content()
                    Spacer(Modifier.height(Space.lg))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End),
                    ) {
                        buttons()
                    }
                }
            }
        }
    }
}

private val MAX_WIDTH = 360.dp

/**
 * Darker than a sheet's.
 *
 * A sheet leaves most of the page showing and is read against it; an alert is a
 * question that has to be answered before anything else, and the page behind it
 * is deliberately pushed back.
 */
private val SCRIM = Color.Black.copy(alpha = 0.6f)
