package com.mytube.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.theme.Tokens

/**
 * What somebody is part way through, as a row that scrolls sideways.
 *
 * ## Why a rail and not a grid
 *
 * The web app tried it as a grid and wrote down why it changed: *"as twelve
 * cards this section arrived instead of the feed rather than before it."* A
 * dozen half-watched videos above the feed is a second feed; a row you glance
 * along is a shelf.
 *
 * ## Why it is absent rather than empty
 *
 * A heading over nothing is a section that looks broken. Somebody who has
 * finished everything they started should see the feed at the top of the page,
 * which is where it belongs.
 */
@Composable
fun ContinueWatchingRail(
    videos: List<Video>,
    mediaBaseUrl: String,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    val strings = LocalStrings.current

    Column(modifier) {
        Text(
            text = strings.continueWatching,
            color = Tokens.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = Space.lg, bottom = Space.md),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            items(videos, key = { it.id }) { video ->
                RailCard(video, mediaBaseUrl) { onOpenVideo(video.id) }
            }
        }

        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun RailCard(video: Video, mediaBaseUrl: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            // Wide enough that the next card peeks in at the right edge, which
            // is what tells a thumb the row scrolls. A card that ends flush with
            // the screen looks like the end of the list.
            .width(240.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radius.thumbnail))
                .background(Tokens.surface),
        ) {
            AsyncImage(
                model = "$mediaBaseUrl/media/${video.thumbnailPath}",
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
            // Always drawn here, unlike on a feed card. Every video in this rail
            // is by definition part way through, so the bar is the reason the
            // card is in the row at all.
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

        Spacer(Modifier.height(Space.sm))
        Text(
            text = video.title,
            color = Tokens.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = video.channel.name,
            color = Tokens.text2,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
