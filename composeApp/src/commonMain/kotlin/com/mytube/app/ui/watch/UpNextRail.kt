package com.mytube.app.ui.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.BadgeBackground
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.home.formatRelative
import com.mytube.app.ui.home.formatViews
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.glassControl
import com.mytube.app.ui.shell.skeletonShade
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.shell.pressableGlassControl

/** How recently ingested still counts as new. Two days, from the web app. */
private const val NEW_WINDOW_DAYS = 2

/**
 * What plays after this, in the shape the web app gives it.
 *
 * Three parts, and each was missing here: a header naming the next video, a
 * pair of chips filtering the list to this channel, and rows that are
 * **horizontal** — a 168dp thumbnail with the text beside it, not the
 * full-width cards the feed uses.
 *
 * The card shape is the difference that matters. A rail of full-width cards is
 * a second feed under the video: it fills the screen, so scrolling to the third
 * suggestion is a journey, and it competes with the video for attention rather
 * than sitting beside it.
 */
@Composable
fun UpNextRail(
    current: Video,
    videos: List<Video>,
    collapsed: Boolean,
    channelOnly: Boolean,
    /** The rail's own request is still out. It is fetched after the picture. */
    loading: Boolean,
    mediaBaseUrl: String,
    onToggleCollapsed: () -> Unit,
    onSelectFilter: (Boolean) -> Unit,
    onOpenVideo: (String) -> Unit,
    /** Today, as an ISO date, so the "New" badge can be decided without a clock. */
    today: String,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    val shade = skeletonShade()
    val turn by animateFloatAsState(if (collapsed) 0f else 180f, label = "up-next-chevron")

    Column(modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressableGlassControl(RoundedCornerShape(12.dp), onClick = onToggleCollapsed)
                .padding(horizontal = Space.md, vertical = Space.sm)
                // The header is two lines with an answer and one without, and
                // switching filters puts it through both — so pressing a chip
                // made the pane jump shorter and then taller again under the
                // thumb that pressed it. It keeps both lines throughout now, and
                // this is what carries it between the two heights when a title
                // is short enough to need only one.
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    // "Next: nothing queued" while the request is still out is
                    // an answer nobody has been given yet. The plain heading
                    // says only what is true, and the title arrives with the
                    // rows below it.
                    text = if (loading) {
                        strings.upNext
                    } else {
                        strings.nextUp(videos.firstOrNull()?.title ?: strings.nothingQueued)
                    },
                    color = Tokens.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val next = videos.firstOrNull()
                when {
                    next != null -> Text(
                        text = next.channel.name,
                        color = Tokens.text2,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The second line is held open while the answer is on its
                    // way, as a bar rather than as empty space: the row is a
                    // pane, and a pane that grows a line when a request lands
                    // moves everything under a thumb already reaching for it.
                    loading -> Box(
                        Modifier
                            .padding(top = 3.dp)
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shade),
                    )
                }
            }
            Icon(
                imageVector = ChevronIcon,
                contentDescription = strings.upNext,
                tint = Tokens.text,
                // Down when folded and up when open, turning between the two —
                // the same two positions and the same turn as the comments
                // heading above it. It pointed right and snapped, which made two
                // sections a thumb's width apart read as two different controls.
                modifier = Modifier.size(20.dp).rotate(turn),
            )
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(SECTION_SPRING) + fadeIn(),
            exit = shrinkVertically(SECTION_SPRING) + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(Space.md))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    FilterChip(strings.allSources, !channelOnly) { onSelectFilter(false) }
                    FilterChip(strings.fromChannel(current.channel.name), channelOnly) {
                        onSelectFilter(true)
                    }
                }

                Spacer(Modifier.height(Space.md))
                if (loading && videos.isEmpty()) {
                    UpNextSkeleton()
                } else {
                    videos.forEach { video ->
                        SuggestionRow(video, mediaBaseUrl, today) { onOpenVideo(video.id) }
                        Spacer(Modifier.height(Space.sm))
                    }
                }
            }
        }
    }
}

/**
 * The rail, before it arrives.
 *
 * The same 168dp box and the same three lines beside it as [SuggestionRow], so
 * nothing moves when the answer lands — which is the whole promise a skeleton
 * makes, and the reason this one is not three plain grey bands.
 */
@Composable
private fun UpNextSkeleton() {
    val shade = skeletonShade()
    Column(Modifier.fillMaxWidth()) {
        repeat(3) {
            Row(Modifier.fillMaxWidth().padding(Space.xs)) {
                Box(
                    Modifier
                        .width(168.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shade),
                )
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    SkeletonBar(shade, 1f)
                    Spacer(Modifier.height(Space.xs))
                    SkeletonBar(shade, 0.7f)
                    Spacer(Modifier.height(Space.xs))
                    SkeletonBar(shade, 0.45f)
                }
            }
            Spacer(Modifier.height(Space.sm))
        }
    }
}

@Composable
private fun SkeletonBar(shade: Color, width: Float) {
    Box(
        Modifier
            .fillMaxWidth(width)
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(shade),
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Tokens.bg else Tokens.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .pressableGlassControl(
                RoundedCornerShape(percent = 50),
                selected = selected,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun SuggestionRow(
    video: Video,
    mediaBaseUrl: String,
    today: String,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(Space.xs),
    ) {
        Box(Modifier.width(168.dp).aspectRatio(16f / 9f)) {
            AsyncImage(
                model = imageModel(mediaBaseUrl, video.thumbnailPath),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Tokens.surface),
            )
            // Nothing when the length is not known — the same rule the feed's
            // card follows, and the reason a live row does not read "0:00".
            if (video.durationSeconds > 0) {
                Text(
                    text = formatDuration(video.durationSeconds),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BadgeBackground)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        Spacer(Modifier.width(Space.sm))
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
            Spacer(Modifier.height(2.dp))
            Text(video.channel.name, color = Tokens.text2, fontSize = 12.sp, maxLines = 1)
            Text(
                text = listOfNotNull(
                    formatViews(video.viewCount, strings).takeIf { video.viewCount > 0 },
                    formatRelative(video.publishedAt, strings).takeIf { video.hasPublishedDate },
                ).joinToString(" • "),
                color = Tokens.text2,
                fontSize = 12.sp,
                maxLines = 1,
            )
            // "New" means recently *ingested*, not recently published. The
            // window is tight on purpose: a badge on every row decorates
            // instead of telling anybody anything.
            if (isNewlyAdded(video.addedAt, today)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.newBadge,
                    color = Tokens.text2,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Tokens.surface)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Whether a video arrived in the library within the last couple of days.
 *
 * Compared as text on the date alone — `"2026-08-29" >= "2026-08-27"` is a
 * correct answer for ISO-8601, and it needs neither a date library nor a
 * timezone. What it needs is today's date, which is why that is a parameter:
 * `commonMain` has no clock without a dependency, and a badge computed from a
 * guess would be wrong on exactly the days it matters.
 */
internal fun isNewlyAdded(addedAt: String, today: String): Boolean {
    if (addedAt.length < 10 || today.length < 10) return false
    return addedAt.substring(0, 10) >= minusDays(today.substring(0, 10), NEW_WINDOW_DAYS)
}

/**
 * An ISO date, some days earlier.
 *
 * Arithmetic on the day number with a fixed 31-day month, which is wrong at a
 * month boundary by up to three days — and deliberately so. The alternative is a
 * calendar, and what this decides is whether to draw a small grey label; being a
 * day out on the 1st of a month costs nothing, and a dependency costs a
 * dependency.
 */
private fun minusDays(iso: String, days: Int): String {
    val day = iso.substring(8, 10).toIntOrNull() ?: return iso
    if (day > days) return iso.substring(0, 8) + (day - days).toString().padStart(2, '0')
    val month = iso.substring(5, 7).toIntOrNull() ?: return iso
    if (month > 1) {
        val prev = (month - 1).toString().padStart(2, '0')
        return iso.substring(0, 5) + prev + "-" + (31 - (days - day)).toString().padStart(2, '0')
    }
    val year = iso.substring(0, 4).toIntOrNull() ?: return iso
    return "${year - 1}-12-" + (31 - (days - day)).toString().padStart(2, '0')
}
