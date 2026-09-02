package com.mytube.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.theme.Tokens

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
 *    bright frame. It went 0.45 → 0.32 when the sheen and the rim were added to
 *    carry some of the work, then back up to 0.40 when the frame-wide scrim
 *    under all of this was removed: the pane is the only thing between a white
 *    glyph and a sunlit shot now, and it has to be able to hold that alone.
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
 * # On iOS 26 this is now the fallback, not the answer — see [GlassPane]
 *
 * The paragraph above is still true of *this* modifier and of every platform it
 * runs on. What changed is that a fourth arrangement was measured and works: a
 * SwiftUI `.glassEffect()` laid over the **whole** Compose hosting view does
 * sample the `AVPlayerLayer`, live. So on iOS 26 the controls over the picture
 * go through [GlassPane], which keeps Compose's layout and hands the painting to
 * the platform; `glassSurface` is what Android and every earlier iOS draw.
 *
 * The two costed-and-refused routes are still refused, and for the reason given:
 * a `UIVisualEffectView` inside `VideoContainer`, and a TextureView plus
 * `RenderEffect` on Android, both work by pushing the position and shape of
 * every control down into the platform layer. [GlassPane] pushes a rectangle and
 * keeps the layout in Kotlin, which is the difference.
 */
fun Modifier.glassSurface(shape: Shape, press: GlassPress? = null): Modifier = this
    .clip(shape)
    .drawBehind {
        // Brighter while it is held, for [glassControl]'s reason: paint cannot
        // refract, so what a press changes is the light on the surface.
        val pressed = press?.fraction ?: 0f
        drawRect(GLASS_BASE)
        // A diagonal rather than a vertical: a vertical gradient on a 48dp disc
        // reads as a shadow under it, which is the opposite of light landing on
        // a surface. `Offset.Infinite` lets the brush size itself to whatever it
        // is drawn into, so one modifier serves a 52dp disc and a 120dp pill.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    GLASS_SHEEN.copy(alpha = GLASS_SHEEN.alpha + SURFACE_LIFT * pressed),
                    Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        )
    }
    .border(1.dp, GLASS_EDGE, shape)

/** How much brighter the sheen gets while a disc over the video is held. */
private const val SURFACE_LIFT = 0.10f

private val GLASS_BASE = Color.Black.copy(alpha = 0.40f)
private val GLASS_SHEEN = Color.White.copy(alpha = 0.16f)
private val GLASS_EDGE = Color.White.copy(alpha = 0.22f)

/**
 * The same material, for a control that sits **on the page** rather than over
 * the video.
 *
 * # Why there are two, and not one
 *
 * [glassSurface] is built for a bright, moving backdrop: it starts from black,
 * because a white glyph over a sunlit frame needs something behind it. A pill on
 * this app's page has the opposite problem — the page is `#0F0F0F` — so the same
 * recipe would be a black button on a black background. This one starts from
 * *white* at a low alpha, which over the page reads as a raised surface and over
 * a thumbnail scrolling past lets the colour through.
 *
 * # Why it is paint and not a sampled backdrop
 *
 * A real one would sample the layer the shell records, and these controls are
 * *inside* that layer — a button would be sampling a recording of itself. The
 * sampled material belongs to surfaces that float **over** the page and are
 * outside its recording: the two bars, the miniplayer and the sheet, all of
 * which go through [BarBackdrop]. Everything on the page is this.
 *
 * # Selected is a change of fill, not of tint
 *
 * `selected` swaps to the app's inverted surface — a solid light fill with dark
 * content on it. That is deliberate and it is the lesson the Like button cost:
 * `surface` and `surfaceHover` were six units apart, which is invisible as a
 * state. A state has to be a different *kind* of surface, not a slightly
 * different shade of the same one.
 */
fun Modifier.glassControl(
    shape: Shape,
    selected: Boolean = false,
    /**
     * How hard it is being pressed, or null for a surface nobody presses.
     *
     * Paint cannot refract, so what deepens here is the light on it: the fill
     * and the sheen brighten while a finger is down. It is the same [GlassPress]
     * the squash reads, so a control wearing both moves as one thing.
     */
    press: GlassPress? = null,
): Modifier =
    if (selected) {
        this.clip(shape).background(Tokens.invertBg)
    } else {
        this
            .clip(shape)
            .drawBehind {
                val pressed = press?.fraction ?: 0f
                drawRect(CONTROL_FILL.copy(alpha = CONTROL_FILL.alpha + PRESS_LIFT * pressed))
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            CONTROL_SHEEN.copy(alpha = CONTROL_SHEEN.alpha + PRESS_LIFT * pressed),
                            Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                )
            }
            .border(1.dp, CONTROL_EDGE, shape)
    }

/** How much brighter a painted surface gets while it is held. */
private const val PRESS_LIFT = 0.06f

private val CONTROL_FILL = Color.White.copy(alpha = 0.09f)
private val CONTROL_SHEEN = Color.White.copy(alpha = 0.05f)
private val CONTROL_EDGE = Color.White.copy(alpha = 0.10f)
