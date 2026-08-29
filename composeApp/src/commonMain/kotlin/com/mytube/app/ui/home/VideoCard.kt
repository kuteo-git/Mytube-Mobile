package com.mytube.app.ui.home

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.i18n.Strings
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
) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier
                .padding(horizontal = Space.lg)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radius.thumbnail))
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
            modifier = Modifier.padding(
                start = Space.lg,
                end = Space.sm,
                top = Space.md,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            AsyncImage(
                model = imageModel(mediaBaseUrl, video.channel.avatarPath),
                contentDescription = video.channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(Size.avatar).clip(CircleShape).background(Tokens.surface),
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
                Text(
                    text = video.channel.name,
                    color = Tokens.text2,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Views and age on their own line, which is what the card
                // specification says and what the web draws. The first version
                // put the channel and the views on one line, which fits but
                // reads as a different app.
                Text(
                    text = metaLine(video, strings),
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
            Icon(
                imageVector = MoreVertical,
                contentDescription = strings.moreOptions,
                tint = Tokens.text2,
                modifier = Modifier.size(Size.iconButton).padding(Space.sm),
            )
        }

        // 40px between cards, from the grid's vertical gap. It is what makes a
        // feed read as a list of separate things rather than a wall.
        Spacer(Modifier.height(Space.xxl))
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

object Size {
    /** In a card. The watch page uses 40 and a comment 24. */
    val avatar = 36.dp
    val iconButton = 40.dp
    val topBar = 56.dp
    val chip = 32.dp
}

/** `--badge-bg`: rgba(0,0,0,0.80). */
val BadgeBackground = Color.Black.copy(alpha = 0.8f)
