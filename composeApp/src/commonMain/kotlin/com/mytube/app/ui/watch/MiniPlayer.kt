package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.BarBackdrop
import com.mytube.app.ui.shell.GLASS_MARGIN
import com.mytube.app.ui.shell.GLASS_SHAPE
import com.mytube.app.ui.shell.pressSquish
import com.mytube.app.ui.shell.rememberLandingKnock
import com.mytube.app.ui.shell.rememberGlassPress
import com.mytube.app.ui.theme.Tokens

/**
 * The bar's height, which is what the picture inside it is sized from.
 *
 * `Size.topBar` rather than a number of its own, because this capsule rests
 * directly on the tab bar's capsule and the two are read as one set. It was
 * 64dp against that bar's 56, and eight units of difference between two panes
 * sharing an edge is the seam the charter has already recorded twice — once
 * between these same two bars, in tint rather than in height.
 *
 * Taking the constant rather than writing 56 is the same rule: the charter
 * records the web app learning four separate times that a bar's height belongs
 * in exactly one place.
 */
private val MINI_HEIGHT = Size.topBar

/** The shell's margin, so this pane lines up with the two bars. */
val MINI_SIDE_MARGIN = GLASS_MARGIN

/**
 * How far the round window is inset from the capsule, on every side.
 *
 * Public because the drag reads it: the travelling picture has to land on the
 * *window*, not on the bar's top edge, and it was landing this much above it.
 *
 * **One number for all four sides, and the left used to be 14.** The argument
 * for the larger left inset was that a true capsule's left edge curves away
 * from the corners of anything reaching its top and bottom, so a picture
 * starting where the top one does would have its corners clipped. That is
 * correct for a *square*, and this window is a circle. A circle of radius
 * `H / 2 - p` centred on the capsule's own left arc centre is concentric with
 * that arc: the gap between the two curves is exactly `p` the whole way round,
 * and nothing is clipped. Its bounding box then starts `p` from the left, which
 * is the same `p` as above and below.
 *
 * So the extra six units were not clearance, they were an off-centre picture —
 * reported as the padding at the top and bottom looking smaller than the one at
 * the left, which is precisely what it was.
 */
val MINI_THUMB_PAD = 8.dp

/**
 * The air between this capsule and the bar under it.
 *
 * `Size.miniPlayer` is built from it, so the space every list reserves and the
 * place the drag aims at both already include it — which is why the number
 * lives there rather than here.
 */
private val MINI_GAP = Size.miniGap

/**
 * How wide the round window is, and how far in from the screen's edge its left
 * side sits. It is square, so the height is the same number.
 *
 * The drag has to put the video down on exactly this rectangle, and it used to
 * aim at a hard-coded 0.3 of the screen width "derived against a phone's width"
 * — an approximation that was invisible only because the bar was full-bleed and
 * the picture started at x=0. A floating capsule has a real left margin and a
 * real inset picture, so the numbers are exported and the fraction is computed
 * from the layer's measured width instead of guessed.
 */
val MINI_THUMB_HEIGHT = MINI_HEIGHT - MINI_THUMB_PAD * 2
val MINI_THUMB_LEFT = MINI_SIDE_MARGIN + MINI_THUMB_PAD


/**
 * The video, shrunk to a bar above the tab bar.
 *
 * ## Why this exists
 *
 * Dragging the watch screen away already left the sound playing — `release()`
 * lets go of the connection and deliberately does not stop the service — and
 * that was half a feature: the audio continued with nothing on screen to say so,
 * and the only way back to the video, or to stop it, was the notification. This
 * is the missing half, and it is why the watch screen is now *collapsed* rather
 * than dismissed.
 *
 * ## Why the picture keeps playing in it
 *
 * The same `VideoSurface`, pointed at the same player. It is not a thumbnail
 * standing in for one: `PlayerView` rebinds on update, so moving between the
 * full screen and this bar re-attaches to a connection that never dropped. A
 * still image would be cheaper and would misreport a paused video as a playing
 * one.
 *
 * ## Why there is a close button and not only a swipe
 *
 * Swiping a bar away is the gesture people expect and it is discoverable by
 * nobody. The X is the way somebody finds; the gesture can be added beside it.
 */
@Composable
fun MiniPlayer(
    player: VideoPlayer,
    title: String,
    channel: String,
    progress: Float,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    /**
     * Space kept clear under the row, inside the bar's own glass.
     *
     * The navigation inset when there is no tab bar beneath this, and **zero
     * when there is** — the tab bar keeps clear of the home indicator itself,
     * and adding it here as well made the bar 34dp taller than it looked. That
     * extra height was drawn with the backdrop and sat exactly over the tab
     * bar's icon row, which is why the icons disappeared and the player looked
     * like it was covering the bar.
     *
     * It is the caller's to compute because only the app knows whether there is
     * a tab bar under this and how far it has scrolled away.
     */
    bottomInset: Dp = 0.dp,
    /**
     * Whether the picture is drawn here, or only the black box it sits in.
     *
     * False for exactly one caller: the copy of this bar drawn *underneath* the
     * watch layer while the drag is in progress. Both platforms bind a player to
     * one surface and one only — Android's `PlayerView` and iOS's
     * `AVPlayerLayer` — so two `VideoSurface`s pointed at the same player means
     * the second steals it and the first goes black. During the drag the watch
     * screen is the one holding it, and the real video is travelling down into
     * precisely this box, so the box is covered by it and never seen empty.
     *
     * **No default.** A flag whose default is the common case is a flag the one
     * call site that needed the other value forgets to pass, with the compiler
     * saying nothing — which is how `startAtBeginning` resumed a video twelve
     * minutes in.
     */
    showSurface: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    // A capsule that floats, not a strip that spans the screen.
    //
    // The shape was copied from the platform once the tab bar became a system
    // one: iOS 26 draws bars as rounded panes inset from the edges, and Music's
    // own mini player is exactly this — a capsule resting just above the tab
    // bar's capsule, with the page visible down both sides. A full-width strip
    // beside a floating capsule reads as two different kinds of surface stacked
    // on each other, which is the fault that was corrected between this bar and
    // the tab bar two days ago, in a different form.
    //
    // The margins are *outside* the glass. `bottomInset` used to be padding
    // *inside* it, so the pane stretched to the bottom of the screen when the
    // tab bar was gone; a floating capsule must not stretch — it keeps its shape
    // and moves.
    // The whole pane gives under a finger, as every other pane in this app
    // does. The squash is on the capsule and not on the row inside it, because
    // what is being pressed is the sheet of glass — the picture and the two
    // lines of text travel with it because they are on it.
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    // A knock, not a tick.
    //
    // `Haptics.kt` draws the line: a *selection* is a value moving through
    // discrete positions, an *impact* is something arriving and stopping.
    // Pressing this bar is the video coming back to full screen, which is the
    // same arrival the drag's landing already knocks for — and the tick is what
    // the tab bar and the chips use, where the press changes which of several
    // things is chosen. There is nothing being chosen here.
    val knock = rememberLandingKnock()
    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = MINI_SIDE_MARGIN,
                end = MINI_SIDE_MARGIN,
                bottom = bottomInset + MINI_GAP,
            )
            .pressSquish(press)
            .clip(MINI_SHAPE)
            // The whole bar reopens the video. A target this size wants one
            // meaning, and the two buttons on it carve out their own.
            .clickable(
                interactionSource = source,
                indication = null,
                onClick = { knock(); onExpand() },
            ),
    ) {
    // The same material as the bars, rather than a solid fill. It sits above the
    // tab bar and inherits that bar's job of letting the feed show through — a
    // solid pane between two glass ones reads as a different surface that
    // happens to be the same colour.
    //
    // The shape is passed on, because the lens refracts along the corners: a
    // capsule drawn with a square-cornered backdrop bends light at edges that
    // are not there.
    BarBackdrop(Modifier.matchParentSize(), fromTop = false, shape = MINI_SHAPE)

    Column(Modifier.fillMaxWidth()) {

        Row(
            Modifier.fillMaxWidth().height(MINI_HEIGHT).padding(end = BAR_ROW_END),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A round window, not a 16:9 slot.
            //
            // A circle needs the picture to *fill* it — see `VideoSurface`'s
            // `fill`. Fitted inside, a 16:9 frame in a circle is a stripe with
            // two black caps, which reads as a broken image rather than as a
            // window. Nothing is lost by cropping here: at 40dp nobody is
            // watching the edges of the shot, and the whole frame is one tap
            // away.
            Box(
                Modifier
                    .padding(
                        start = MINI_THUMB_PAD,
                        top = MINI_THUMB_PAD,
                        bottom = MINI_THUMB_PAD,
                    )
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color.Black),
            ) {
                if (showSurface) {
                    VideoSurface(
                        player,
                        Modifier.fillMaxWidth().fillMaxHeight(),
                        fill = true,
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.md),
            ) {
                Text(
                    text = title,
                    color = Tokens.text,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    // One line each. Two would fit and would make the bar a
                    // block of text over a picture the size of a stamp; what is
                    // wanted here is enough to recognise, not enough to read.
                    maxLines = 1,
                    // It scrolls rather than ending in an ellipsis, and only
                    // when it has to: `basicMarquee` measures the text against
                    // the space it was given and animates nothing when it fits.
                    // So a short title is as still as it ever was, and the
                    // condition is the layout's rather than a boolean this
                    // composable would have to keep in step with the width.
                    //
                    // **The title only.** The channel keeps its ellipsis: two
                    // lines travelling in the same direction inside a 40dp-tall
                    // bar is two things asking to be followed, and the one
                    // worth following is the one that names the video.
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = channel,
                    color = Tokens.text2,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            BarButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) PauseIcon else PlayIcon,
                    contentDescription = if (isPlaying) strings.pause else strings.play,
                    tint = Tokens.text,
                    modifier = Modifier.size(22.dp),
                )
            }

            BarButton(onClick = onClose) {
                Icon(
                    imageVector = CloseIcon,
                    contentDescription = strings.close,
                    tint = Tokens.text,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

    }

        // The progress line, on the capsule's bottom edge.
        //
        // It was a full-bleed line across the top. On a capsule that is the one
        // place it cannot go: the corners curve away from it, so the line is
        // clipped to a stub at each end and reads as a rendering fault. The
        // bottom edge is where every player in the world draws one anyway.
        //
        // **The side margin is the corner radius, and that is arithmetic rather
        // than taste.** A `percent = 50` capsule this tall has a radius of half
        // its height, and its bottom edge is straight only between the two
        // corners — exactly `radius` in from each side. A line inset by any less
        // runs into the curve and is clipped; by any more and it is short of the
        // shape for no reason.
        //
        // Drawn as an overlay rather than a row in the Column, so it costs the
        // capsule no height. `Size.miniPlayer` is what the app reserves for this
        // bar and what the drag aims at; a line that added to it would move the
        // landing without anything saying so.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = MINI_HEIGHT / 2)
                .height(MINI_PROGRESS_HEIGHT)
                .clip(RoundedCornerShape(percent = 50))
                .background(Tokens.line),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Tokens.brand))
        }
    }
}

/**
 * How thick the progress line under the bar is.
 *
 * A hairline, and thinner than it was. At 2dp it read as a *control* — something
 * to be dragged — on a bar where the only gestures are a tap to expand and the
 * two buttons. It is a readout, and the watch screen's own bar at rest makes the
 * same argument: a line nobody can touch should look like a line, not a track.
 *
 * Not below 1dp. That is one pixel on a phone that has not had a 1x screen in
 * fifteen years, and it is the point where a red line over a bright thumbnail
 * stops being visible at all.
 */
private val MINI_PROGRESS_HEIGHT = 1.5.dp

/**
 * The capsule's outline.
 *
 * The same shape the bars carry, because the three panes are read as a set — see
 * `GLASS_SHAPE`. A fixed 20dp radius was tried first, on the worry that a true
 * capsule at 64dp tall would eat the corners of the picture inside; it does not,
 * because that picture is a circle inset from the pane's own edges.
 */
private val MINI_SHAPE = GLASS_SHAPE

/**
 * The width of one of the two buttons, and the reason it is not 48.
 *
 * The glyph is 22dp, so a 48dp box leaves 13dp of air on each side of it — and
 * that is what put the X's glyph 13dp from the capsule's right edge while the
 * round window sits 8dp from its left, and left 26dp of nothing between the two
 * glyphs. Reported as the two buttons being too far apart and not matching the
 * padding on the right, which is exactly the arithmetic above.
 *
 * **The right end is a capsule arc, so the thing to mirror is the *centre*, not
 * the edge.** The window's centre is `MINI_THUMB_PAD + MINI_THUMB_HEIGHT / 2`
 * from the left — the capsule's own arc centre, which is where a circle has to
 * sit to be concentric with it. The last glyph's centre is placed the same
 * distance from the right, which is what [BAR_BUTTON_WIDTH] and [BAR_ROW_END]
 * together arrange.
 *
 * Narrower, not shorter: the row has width to spare and 48dp of *height* is
 * what makes a near miss unlikely in the direction a thumb actually strays.
 * 44dp is the platform's own floor, and it is still wider than the 40dp window
 * opposite it.
 */
private val BAR_BUTTON_WIDTH = 44.dp

/** And the height, which stays what it was. */
private val BAR_BUTTON_HEIGHT = 48.dp

/**
 * What is left over at the end of the row.
 *
 * The last glyph's centre lands at `BAR_ROW_END + BAR_BUTTON_WIDTH / 2` from
 * the capsule's right edge, and that has to equal the window's own centre on
 * the other side. Written as the subtraction rather than as a number, so the
 * two sides cannot drift apart the next time either is changed.
 */
private val BAR_ROW_END =
    MINI_THUMB_PAD + MINI_THUMB_HEIGHT / 2 - BAR_BUTTON_WIDTH / 2

/**
 * A 44×48dp target around a 22dp glyph.
 *
 * Both of these sit inside a row that is itself clickable, so they have to be
 * comfortably hittable or a near miss reopens the video instead of pausing it —
 * which is the worst possible outcome of aiming at pause.
 *
 * ## Why it squashes and has no pane of its own
 *
 * The squash for the reason every control in this app has one: without it a
 * button is one people press twice. And it is the *reason the 48dp target above
 * is worth having* — a target nobody can see is only as good as the
 * acknowledgement it gives, and this one gave none.
 *
 * No material, exactly as `TabItem` has none. These glyphs sit **on** the bar's
 * pane; a second pane laid on the first is two sheets of glass at one place,
 * which is the seam this bar and the tab bar were unified to remove. What is
 * left is the movement, which is the half that answers the finger.
 *
 * The press is its own `MutableInteractionSource`, so the bar behind it does not
 * squash too: `clickable` on this Box consumes the down event, and the outer
 * one never starts an interaction. Pressing pause has to look like pressing
 * pause and not like pressing the bar.
 */
@Composable
private fun BarButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    Box(
        Modifier
            .width(BAR_BUTTON_WIDTH)
            .height(BAR_BUTTON_HEIGHT)
            .pressSquish(press)
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
