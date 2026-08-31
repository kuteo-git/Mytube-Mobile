package com.mytube.app.ui.watch

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.BarBackdrop
import com.mytube.app.ui.theme.Tokens

/** The bar's height, which is what the picture inside it is sized from. */
private val MINI_HEIGHT = 64.dp

/**
 * How wide the bar's picture is, as a share of the screen.
 *
 * The drag shrinks the full-width video down to exactly this, so the gesture
 * ends where the bar begins. Derived from the bar's own 16:9 thumbnail against
 * a phone's width — one number, so the two cannot land in different places.
 */
const val MINI_THUMB_FRACTION = 0.3f

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

    Box(
        modifier
            .fillMaxWidth()
            // The whole bar reopens the video. A target this size wants one
            // meaning, and the two buttons on it carve out their own.
            .clickable(onClick = onExpand),
    ) {
    // The same frosted material as the bars, rather than a solid fill. It sits
    // on the tab bar and inherits that bar's job of letting the feed show
    // through — a solid strip between two frosted ones reads as a different
    // surface that happens to be the same colour.
    BarBackdrop(Modifier.matchParentSize(), fromTop = false)

    Column(Modifier.fillMaxWidth().padding(bottom = bottomInset)) {
        // A line, not a bar. It says how far through without asking for any of
        // the 64dp the row needs, and it is the only thing here that moves.
        Box(Modifier.fillMaxWidth().height(2.dp).background(Tokens.line)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Tokens.brand))
        }

        Row(
            Modifier.fillMaxWidth().height(MINI_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                if (showSurface) {
                    VideoSurface(player, Modifier.fillMaxWidth().fillMaxHeight())
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
                    overflow = TextOverflow.Ellipsis,
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
    }
}

/**
 * A 48dp target around a 22dp glyph.
 *
 * Both of these sit inside a row that is itself clickable, so they have to be
 * comfortably hittable or a near miss reopens the video instead of pausing it —
 * which is the worst possible outcome of aiming at pause.
 */
@Composable
private fun BarButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
