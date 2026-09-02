package com.mytube.app.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mohamedrejeb.calf.ui.gesture.adaptiveClickable
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.shell.MenuAction
import com.mytube.app.ui.shell.rememberMenuAnchor
import com.mytube.app.ui.theme.Tokens

/**
 * One video in the feed.
 *
 * Every number here is from `design-system/local-youtube/MASTER.md`, the same
 * file the web app builds against, and the section that describes this card:
 *
 * > *thumbnail (radius 12, badge duration góc dưới-phải `--badge-bg` radius 4,
 * > progress bar đỏ nếu xem dở) → hàng meta: avatar 36px + khối text (title 2
 * > dòng clamp / channel / `views · time`)*
 *
 * Copying the file rather than eyeballing the web app is the difference between
 * two clients that match and two that nearly match. The first version of this
 * card was eyeballed: square corners, edge to edge, and one meta line instead of
 * two — close enough to look right alone and plainly wrong beside the web.
 */
@Composable
fun VideoCard(
    video: Video,
    mediaBaseUrl: String,
    strings: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The overflow menu's two actions, or null for a card that has none.
     *
     * Null draws **no button at all**, which is the point: this icon was drawn
     * unconditionally and wired to nothing, so pressing it did nothing — the one
     * thing §5 of the server charter forbids outright. A card in a list of
     * upstream results, where neither action means anything, now has no dot
     * rather than a dead one.
     */
    onSave: (() -> Unit)? = null,
    /**
     * What the save row is called, when "Save" is the wrong word for it.
     *
     * Empty everywhere but the saved shelf, where every row is already kept and
     * the menu was offering "Saved" — a statement of what is already true, with
     * no verb to press.
     */
    saveLabel: String = "",
    onNotInterested: (() -> Unit)? = null,
    /**
     * Open the channel this video belongs to.
     *
     * Its own target on the avatar, not the whole row. The avatar is the one
     * part of a card that *is* the channel — pressing it and getting the video
     * is the thing people describe as "the avatar does nothing", because
     * something did happen and it was not what they aimed at.
     */
    onOpenChannel: (() -> Unit)? = null,
    /**
     * Its row is being written into the catalogue, so it cannot be opened yet.
     *
     * True only on the channel page, whose rows come from YouTube — see
     * `ChannelViewModel.openVideo`. The card says so over its own picture and
     * stops taking presses, because writing the row is a round trip to YouTube
     * and a card that looks idle is one somebody presses again.
     *
     * `ExternalVideoCard` draws the same thing for the search screen's upstream
     * half, which is the same state on the other list that has it.
     */
    opening: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Pressed state, read here rather than left to the default ripple.
    //
    // Measured off the YouTube app: pressing a card fills the **meta row** with
    // `#272727`, edge to edge, and leaves the thumbnail alone. A ripple spreading
    // over a photograph is invisible anyway, and one spreading over the whole
    // card is a different shape from what is being copied.
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()

    // The card settles very slightly under a finger and springs back when it is
    // let go — the acknowledgement every native list has and this one did not.
    //
    // 0.98, not a figure anybody would name if asked: a card that visibly
    // shrinks reads as a button, and this is a picture. What it has to do is
    // confirm the touch landed *here* rather than on the card above, in the
    // moment before the player rises. A spring rather than a tween, because
    // release is the half people feel.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card-press",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Calf's, not Compose's. On iOS it replaces the ripple with the
            // scale UIKit applies to a pressed cell, which is what the
            // hand-rolled 0.98 spring below was reaching for — that stays,
            // because Android gets nothing from this and still needs to say the
            // touch landed.
            .adaptiveClickable(
                interactionSource = press,
                indication = null,
                enabled = !opening,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                // Edge to edge, square. The design system's 12dp radius and side
                // margin describe the *web* card; on the phone app the picture
                // runs into both edges of the screen, which is what makes a feed
                // read as a column of pictures rather than a list of tiles.
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Tokens.surface),
        ) {
            AsyncImage(
                model = imageModel(mediaBaseUrl, video.thumbnailPath),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )

            if (video.durationSeconds > 0) {
                Text(
                    text = formatDuration(video.durationSeconds),
                    color = Tokens.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Space.sm)
                        .clip(RoundedCornerShape(Radius.badge))
                        .background(BadgeBackground)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            if (opening) OpeningOverlay()

            // How far through, drawn in brand red across the bottom of the
            // thumbnail. Only for a video somebody has actually started: a
            // hairline at zero on every card would be noise, and one at the far
            // end on a finished video is a fact nobody needs on a feed.
            if (video.watchedFraction > 0.01) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Tokens.line),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(video.watchedFraction.toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Tokens.brand),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The pressed fill goes on the row, edge to edge, before the
                // padding — so it reaches the screen's edges rather than stopping
                // where the text starts.
                .background(if (pressed) Tokens.surfaceHover else Color.Transparent)
                .padding(
                    start = Space.lg,
                    end = Space.sm,
                    top = Space.md,
                    bottom = Space.md,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            ChannelAvatar(
                name = video.channel.name,
                mediaBaseUrl = mediaBaseUrl,
                path = video.channel.avatarPath,
                size = Size.avatar,
                modifier = if (onOpenChannel != null) {
                    Modifier.clickable(onClick = onOpenChannel)
                } else {
                    Modifier
                },
            )
            Spacer(Modifier.width(Space.md))

            Column(Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = Tokens.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // Channel, views and age on **one** line, joined by "·".
                //
                // Two lines is what the web card does and what this drew until
                // now. The phone app puts all three together, and the reason is
                // visible on a narrow screen: two grey lines under a two-line
                // title is four lines of text per card, which turns a column of
                // pictures into a wall of writing.
                Text(
                    text = listOfNotNull(
                        video.channel.name.takeIf { it.isNotEmpty() },
                        metaLine(video, strings).takeIf { it.isNotEmpty() },
                    ).joinToString(" · "),
                    color = Tokens.text2,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The overflow button. On the web it appears on hover or focus;
            // a phone has neither, so it is always there — which is what the
            // web app itself shows on a phone.
            VideoCardMenu(video, strings, onSave, saveLabel, onNotInterested)
        }

        // The gap between cards. Smaller than the web's 40px because the meta
        // row now carries its own bottom padding — the pressed fill has to reach
        // the bottom of the row, so the space below the text belongs *inside*
        // it rather than between the cards.
        Spacer(Modifier.height(Space.md))
    }
}

/** `157K views · 1 day ago`, or just the views when the date is unknown. */
private fun metaLine(video: Video, strings: Strings): String {
    val views = formatViews(video.viewCount, strings)
    if (!video.hasPublishedDate) return views
    val age = formatRelative(video.publishedAt, strings)
    return if (age.isEmpty()) views else "$views · $age"
}

/** Spacing, from the design system's scale. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp

    /** The vertical gap between cards in the grid. */
    val xxl = 40.dp
}

object Radius {
    val thumbnail = 12.dp
    val badge = 4.dp
    val chip = 8.dp
}

/**
 * The overflow menu on a card.
 *
 * Its own composable because two different cards draw it — the feed's and the
 * Continue watching rail's — and the rail was missing it entirely. A card that
 * looks like the others and has one fewer control is a card somebody presses
 * twice before deciding it is broken.
 *
 * Null callbacks draw **no button at all**. A card in a list of upstream
 * results, where neither action means anything, gets no dot rather than a dead
 * one.
 */
@Composable
fun VideoCardMenu(
    video: Video,
    strings: Strings,
    onSave: (() -> Unit)?,
    saveLabel: String = "",
    onNotInterested: (() -> Unit)?,
    onMarkWatched: (() -> Unit)? = null,
) {
    if (onSave == null && onNotInterested == null && onMarkWatched == null) return

    // The button is here; the panel is drawn at the root of the app.
    //
    // It was a `DropdownMenu`, which is a popup — its own layer with its own
    // coordinate space — and a sampled backdrop there reads the wrong slice of
    // the screen, while this anchor sits inside the layer the shell records,
    // where sampling is a segfault. Both are facts about *where a popup draws*,
    // so the menu moved instead of settling for paint. See [MenuHost].
    val (anchor, show) = rememberMenuAnchor()

    val items = buildList {
        if (onSave != null) {
            add(
                MenuAction(
                    // "Lưu vào playlist", because the press no longer writes one
                    // bit — it opens the question that bit was standing in for.
                    // So the label does not follow `video.saved` any more: a
                    // video already on the shelf can still be wanted in a
                    // collection, and "Saved" would say the question had been
                    // answered.
                    //
                    // Unless the caller names it: on the saved shelf and on a
                    // playlist page the row is the way *off* the list, which is
                    // a different sentence.
                    label = saveLabel.ifEmpty { strings.saveToPlaylist },
                    onClick = onSave,
                ),
            )
        }
        if (onMarkWatched != null) add(MenuAction(strings.markWatched, onMarkWatched))
        if (onNotInterested != null) add(MenuAction(strings.notInterested, onNotInterested))
    }

    Icon(
        imageVector = MoreVertical,
        contentDescription = strings.moreOptions,
        tint = Tokens.text2,
        modifier = anchor
            .size(Size.iconButton)
            .clip(CircleShape)
            .clickable { show(items) }
            .padding(Space.sm),
    )
}

/**
 * "Fetching this one's metadata", drawn over a card's picture.
 *
 * One composable because **two lists have this state and they must not look
 * different**: the search screen's upstream results and the channel page's
 * uploads, both of which press a video the catalogue has no row for yet. They
 * were two copies with two spinners — Material's here, the platform's there —
 * and it was reported as exactly that.
 *
 * Over the picture rather than beside the title: the picture is what was
 * pressed, and it is the one part of a card big enough to say "this one, and it
 * is working" without a second glance.
 */
@Composable
fun BoxScope.OpeningOverlay() {
    Box(
        Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Tokens.text, modifier = Modifier.size(28.dp))
    }
}

object Size {
    /** In a card. The watch page uses 40 and a comment 24. */
    val avatar = 36.dp
    val iconButton = 40.dp
    val topBar = 56.dp

    /**
     * The miniplayer bar's height, which lists must leave room for.
     *
     * Here rather than beside the composable that draws it, because the thing
     * that has to know is every scrolling screen — and a constant defined next
     * to its only *reader* is one nobody finds when they add the next screen.
     */
    val miniPlayer = 70.dp
    val chip = 32.dp

    /**
     * The pinned chip row, including the padding above and below it.
     *
     * Named here because two places need it and they must not disagree: the row
     * itself, and the feed's top inset, which has to clear it. The charter
     * records the web app learning four separate times that a bar's height
     * belongs in exactly one place.
     */
    val chipRow = chip + 24.dp
}

/** `--badge-bg`: rgba(0,0,0,0.80). */
val BadgeBackground = Color.Black.copy(alpha = 0.8f)
