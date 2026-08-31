package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The material every control over the picture is made of.
 *
 * # It is not a blur, and it cannot be one
 *
 * The reference for this is YouTube's own player, and the thing to notice in a
 * screenshot of it is that **those discs are not frosted either** — they are
 * translucent grey over whatever the frame happens to be. That is fortunate,
 * because a real backdrop blur is impossible in exactly this place: the picture
 * behind these controls is a `UIKitView` on iOS and a `SurfaceView` on Android,
 * neither of which Compose draws into its own layer, so Haze has no pixels of
 * the video to sample. [BarBackdrop] can frost the bars because what passes
 * under *them* is a Compose-drawn feed. Attempting it here would work on one
 * platform, fail silently on the other, and differ at the one place a viewer
 * looks longest.
 *
 * # One material, not three
 *
 * The three groups over the picture — the cluster at the top right, the three
 * transport discs, the clock pill and the fullscreen button — used to carry
 * 0.45, 0.55 and no fill at all. Three shades of black on one frame is the fault
 * that was just corrected between the miniplayer and the tab bar, one screen
 * over: adjacent surfaces at different strengths read as different materials.
 *
 * The hairline is what makes it read as glass rather than as a hole. It is 0.12
 * # Three layers, because one flat fill reads as a hole
 *
 * The first version was a single black at 0.45 with a faint white rim, and it
 * was reported back accurately: it looks *dark*, not like glass. A pane of glass
 * is not a dark rectangle — it is something the light lands on. So:
 *
 * 1. **A dark base**, still, and it is what keeps a white glyph legible over a
 *    bright frame. Lighter than before (0.32 against 0.45) because two more
 *    layers now sit on it.
 * 2. **A luminous wash**, brighter at the top-left corner and fading out toward
 *    the bottom-right. That gradient is the whole illusion: a flat translucent
 *    fill has no direction, and direction is what says a surface is catching
 *    light from somewhere rather than being a window cut in the picture.
 * 3. **A rim**, brighter than it was (0.22 against 0.12) and drawn last so it is
 *    over both.
 *
 * # It is still not a blur, and it cannot be one here
 *
 * The picture behind these controls is an `AVPlayerLayer` inside a `UIKitView`
 * on iOS and a Media3 `PlayerView` on Android. Both are platform views composited
 * *behind* what Compose draws, so [BarBackdrop]'s Haze — which records Compose
 * content — has no video pixels to sample. The bars can be frosted because what
 * passes under them is a Compose-drawn feed; this cannot, and no parameter
 * changes that.
 *
 * A real blur here is reachable and was costed rather than assumed: a
 * `UIVisualEffectView` added inside `VideoContainer` above the player layer on
 * iOS, and a TextureView plus `RenderEffect` on Android. Both would work by
 * pushing the **position and shape of every one of these controls down into the
 * platform layer**, which is a crack straight through the seam §3 of the charter
 * exists to keep — and the Android half is unverified. Deliberately not done for
 * an effect the reference screenshot does not itself have.
 */
fun Modifier.glassSurface(shape: Shape): Modifier = this
    .clip(shape)
    .background(GLASS_BASE)
    .background(
        // A diagonal rather than a vertical: a vertical gradient on a 48dp disc
        // reads as a shadow under it, which is the opposite of light landing on
        // a surface. `Offset.Infinite` lets the brush size itself to whatever it
        // is drawn into, so one modifier serves a 52dp disc and a 120dp pill.
        brush = Brush.linearGradient(
            colors = listOf(GLASS_SHEEN, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Infinite,
        ),
        shape = shape,
    )
    .border(1.dp, GLASS_EDGE, shape)

private val GLASS_BASE = Color.Black.copy(alpha = 0.32f)
private val GLASS_SHEEN = Color.White.copy(alpha = 0.16f)
private val GLASS_EDGE = Color.White.copy(alpha = 0.22f)
