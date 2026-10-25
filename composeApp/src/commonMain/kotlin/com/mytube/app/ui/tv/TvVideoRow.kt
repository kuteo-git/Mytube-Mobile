package com.mytube.app.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.home.metaLine
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.home.formatViews
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.ui.shell.MenuAction
import com.mytube.app.ui.theme.Tokens

/** A library video as a row — search's "In your library" half. */
@Composable
fun TvVideoRow(
    video: Video,
    mediaBaseUrl: String,
    strings: Strings,
    /** See the primitive [TvVideoRow]'s parameter. */
    opening: Boolean,
    onClick: () -> Unit,
    /** See [TvThumbnail]. */
    menu: List<MenuAction>,
    modifier: Modifier = Modifier,
) = TvVideoRow(
    thumbnail = imageModel(mediaBaseUrl, video.thumbnailPath),
    title = video.title,
    channel = video.channel.name,
    meta = metaLine(video, strings),
    durationSeconds = video.durationSeconds,
    watchedFraction = video.watchedFraction,
    opening = opening,
    onClick = onClick,
    menu = menu,
    modifier = modifier,
)

/**
 * An upstream result as a row — search's "On YouTube" half. Upstream sends no
 * date, so the meta line is the views alone, and none at all for a count of zero.
 */
@Composable
fun TvVideoRow(
    video: ExternalVideo,
    strings: Strings,
    opening: Boolean,
    onClick: () -> Unit,
    /** See [TvThumbnail]. */
    menu: List<MenuAction>,
    modifier: Modifier = Modifier,
) = TvVideoRow(
    thumbnail = video.thumbnailUrl,
    title = video.title,
    channel = video.channelName,
    meta = if (video.viewCount > 0) formatViews(video.viewCount, strings) else "",
    durationSeconds = video.durationSeconds,
    watchedFraction = 0.0,
    opening = opening,
    onClick = onClick,
    menu = menu,
    modifier = modifier,
)

/**
 * A video as a row: the picture on the left, the words beside it.
 *
 * Search's results on a television, beside its keyboard: a row reads at that
 * width where the phone's full-width card would be half the screen for one
 * picture. The picture is the focus target and wears the rim, as on a shelf,
 * and a held OK opens the video's menu.
 */
@Composable
fun TvVideoRow(
    /** What the picture loads — a library path already resolved, or an upstream URL. */
    thumbnail: String,
    title: String,
    channel: String,
    meta: String,
    durationSeconds: Int,
    watchedFraction: Double,
    /** Being written into the catalogue: the picture says so and takes no press. */
    opening: Boolean,
    onClick: () -> Unit,
    /** See [TvThumbnail]. */
    menu: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            // Room on the left for the picture's rise, which the list clips:
            // the first build shaved the rim off every focused row's left edge.
            .padding(start = Space.md, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        TvThumbnail(
            thumbnail = thumbnail,
            title = title,
            durationSeconds = durationSeconds,
            watchedFraction = watchedFraction,
            opening = opening,
            onClick = onClick,
            menu = menu,
            modifier = Modifier.width(TV_ROW_THUMB),
        )
        Spacer(Modifier.width(Space.lg))
        // The shelf card's type, for the shelf card's reason: YouTube's TV app,
        // measured.
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Tokens.text,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            if (channel.isNotEmpty()) TvMetaText(channel)
            if (meta.isNotEmpty()) TvMetaText(meta)
        }
    }
}

/**
 * A row's picture: smaller than a shelf card's, so two lines fit beside it in
 * the column search's keyboard leaves.
 */
private val TV_ROW_THUMB = 176.dp
