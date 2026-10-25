package com.mytube.app.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.home.metaLine
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.shell.MenuAction
import com.mytube.app.ui.theme.Tokens

/**
 * A card on a television shelf: the picture, then two lines under it.
 *
 * Not the phone's [com.mytube.app.ui.home.VideoCard]. That one is a full-width
 * picture with the channel's avatar beside the title and an overflow button on
 * the right — a column of pictures read with a thumb. A shelf is read from a
 * sofa, a few cards at a time across the screen, and neither the avatar nor the
 * dot is a target a remote can reach without stopping on two extra places per
 * card. The channel is one press away on the watch screen.
 *
 * The rise and the rim are [pressable]'s, so a card on a shelf answers the
 * remote exactly the way every other control in the app does.
 */
@Composable
fun TvVideoCard(
    video: Video,
    mediaBaseUrl: String,
    strings: Strings,
    onClick: () -> Unit,
    /** See [TvCard]. */
    withMeta: Boolean,
    /**
     * Its row is being written into the catalogue — the channel page's, whose
     * videos come from YouTube. **No default**: the one page that has it is the
     * page a default would forget.
     */
    opening: Boolean,
    /** See [TvThumbnail]. */
    menu: List<MenuAction>,
    modifier: Modifier = Modifier,
    width: Dp = TV_CARD_WIDTH,
) = TvCard(
    thumbnail = imageModel(mediaBaseUrl, video.thumbnailPath),
    title = video.title,
    channel = video.channel.name,
    // Not formatted for a card that draws none (up next).
    meta = if (withMeta) metaLine(video, strings) else "",
    durationSeconds = video.durationSeconds,
    watchedFraction = video.watchedFraction,
    opening = opening,
    onClick = onClick,
    menu = menu,
    withMeta = withMeta,
    modifier = modifier,
    width = width,
)

/**
 * The card itself, from the facts a card shows: a video's, or a playlist's on
 * the television's playlists grid.
 */
@Composable
fun TvCard(
    /** What the picture loads: a resolved library path or an upstream URL. */
    thumbnail: String,
    title: String,
    channel: String,
    meta: String,
    durationSeconds: Int,
    watchedFraction: Double,
    /** See [TvThumbnail]. */
    opening: Boolean,
    onClick: () -> Unit,
    /** See [TvThumbnail]. */
    menu: List<MenuAction>,
    /**
     * Whether the channel and the views are drawn under the title.
     *
     * Yes on a shelf. No in the player's suggestions, where the row shares the
     * screen with the controls and the two meta lines were the part cut off at
     * the bottom of it. **No default**: the rare value is the one a new call
     * site would forget.
     */
    withMeta: Boolean,
    modifier: Modifier,
    /**
     * [TV_CARD_WIDTH], or [TV_CARD_WIDTH_LARGE] on the shelf the remote is on — see
     * [shelfCardWidth] — or `Dp.Unspecified` for a grid cell, which it fills.
     */
    width: Dp,
    /** See [TvThumbnail]. */
    placeholder: ImageVector? = null,
) {
    // Unspecified in a grid, where the card fills the cell — see [TvGrid].
    Column(if (width == Dp.Unspecified) modifier.fillMaxWidth() else modifier.width(width)) {
        TvThumbnail(
            thumbnail = thumbnail,
            title = title,
            durationSeconds = durationSeconds,
            watchedFraction = watchedFraction,
            opening = opening,
            onClick = onClick,
            menu = menu,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
        )
        // The type and the gaps are YouTube's TV app's, measured off a
        // screenshot of it on the household's Chromecast: an 18sp title on a
        // 24sp line, then the channel and the views on two lines of 13sp, the
        // title's first capital twenty dp under the picture.
        Spacer(Modifier.height(TITLE_GAP))
        Text(
            text = title,
            color = Tokens.text,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            // No second line held for a short title. It kept the cards of a
            // shelf on one baseline, and it left a blank line between a
            // one-line title and its channel — read from the sofa as the
            // channel belonging to nothing. YouTube's TV app does not hold one.
            overflow = TextOverflow.Ellipsis,
        )
        if (withMeta) {
            Spacer(Modifier.height(Space.xs))
            if (channel.isNotEmpty()) TvMetaText(channel)
            if (meta.isNotEmpty()) TvMetaText(meta)
        }
    }
}

/** A line of grey under a title: a channel, a count, views and age. */
@Composable
internal fun TvMetaText(text: String) {
    Text(
        text = text,
        color = Tokens.text2,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Picture to title, measured to the title's box rather than its first capital. */
internal val TITLE_GAP = 14.dp

/**
 * How wide the cards on a shelf are: large on the shelf the remote is on, or
 * was last on, and the ordinary size everywhere else.
 *
 * YouTube's TV app does this. A Down moves the next row up to the top of the
 * screen and grows it to two and a half cards across. This used to be "the
 * first shelf is large", which matched YouTube's first screen and no other:
 * reported from the television as every shelf after Continue watching being
 * too small, and measured on the same Chromecast as 720px for YouTube's row
 * in focus against 528px here.
 *
 * [current] is [NO_SHELF] until a shelf has had focus, and then the first
 * shelf is the large one. A named function because the shelf and its skeleton
 * both read it, and a skeleton the wrong size is a page that changes shape
 * when it loads.
 */
internal fun shelfCardWidth(shelfIndex: Int, current: Int): Dp =
    if (shelfIndex == current.coerceAtLeast(0)) TV_CARD_WIDTH_LARGE else TV_CARD_WIDTH

/** No shelf has had focus yet — see [shelfCardWidth]. */
internal const val NO_SHELF = -1

/**
 * How wide an ordinary card on a shelf is: 264dp, YouTube's TV app's own, three
 * and a half across a 960dp television. The half is the point — a row whose
 * last card is cut says there is more to the right without a word.
 */
val TV_CARD_WIDTH = 264.dp

/** The cards on the shelf the remote is on: 360dp, two and a half across, as YouTube's row in focus. */
val TV_CARD_WIDTH_LARGE = 360.dp

/** Between two cards on a shelf. */
val TV_CARD_GAP = 18.dp
