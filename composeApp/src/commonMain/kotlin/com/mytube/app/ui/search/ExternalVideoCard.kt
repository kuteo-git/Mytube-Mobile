package com.mytube.app.ui.search

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mohamedrejeb.calf.ui.gesture.adaptiveClickable
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.ui.home.BadgeBackground
import com.mytube.app.ui.home.Radius
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.mytube.app.ui.home.MoreVertical
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.shell.GlassRadius
import com.mytube.app.ui.shell.menuSurface
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.home.formatViews
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.theme.Tokens

/**
 * One video that is still on YouTube.
 *
 * Its own card rather than [com.mytube.app.ui.home.VideoCard] with empty fields,
 * because upstream sends a different set of facts and the differences are all
 * visible: there is no channel id, so the name is text rather than a target;
 * there is no avatar, and drawing one from an empty path is a request per row
 * for a 404; and there is no published date, so the meta line is two facts
 * rather than three.
 *
 * **No overflow menu.** Saving a video with no catalogue row means writing the
 * row and *then* pinning it — two requests for one press, on a video the
 * household has not decided to keep. The rule the feed's card already follows
 * applies here as it is written: a card whose actions mean nothing draws no dot
 * rather than a dead one.
 */
@Composable
fun ExternalVideoCard(
    video: ExternalVideo,
    strings: Strings,
    /**
     * True while this card's video is being written into the catalogue.
     *
     * Opening one is a real round trip — the gateway asks YouTube for the
     * metadata before there is anything to play — so the card says so and stops
     * taking presses. Navigating first would put the watch screen's "YouTube
     * will not hand this video over" on screen for a video that is on its way.
     */
    opening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Opens the sheet asking which collections to put this in.
     *
     * The one action an upstream result has, and it is not the feed's menu:
     * "not interested" is a statement to the ranker about a *recommendation*,
     * and nothing recommended this. Saving one writes the catalogue row first —
     * see `SavePlaylistViewModel.save` — so it is a round trip the sheet owns
     * rather than two requests fired from a menu item.
     */
    onSaveToPlaylist: (() -> Unit)? = null,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "external-card-press",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .adaptiveClickable(
                interactionSource = press,
                indication = null,
                enabled = !opening,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Tokens.surface),
        ) {
            AsyncImage(
                // Absolute, and pointing at YouTube. Everything else in this app
                // is a path inside the library resolved against the server's
                // address; `imageModel` is for those and would be wrong here.
                model = video.thumbnailUrl,
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

            // Over the picture, not beside the title: the picture is what was
            // pressed, and it is the one part of the card big enough to say
            // "this one, and it is working" without a second glance.
            if (opening) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Tokens.text,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // The dot sits at the end of the title block, as on the library card —
        // one layout for both kinds of result, so a list containing both does
        // not read as two lists.
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (pressed) Tokens.surfaceHover else Color.Transparent)
                .padding(
                    start = Space.lg,
                    end = if (onSaveToPlaylist == null) Space.lg else Space.xs,
                    top = Space.md,
                    bottom = Space.md,
                ),
        ) {
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
                // The channel and the views, and nothing else. Upstream sends no
                // published date on a flat listing, and an empty slot where an
                // age belongs reads as a fact that failed to load.
                text = listOfNotNull(
                    video.channelName.takeIf { it.isNotEmpty() },
                    formatViews(video.viewCount, strings).takeIf { video.viewCount > 0 },
                ).joinToString(" · "),
                color = Tokens.text2,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

            if (onSaveToPlaylist != null) ExternalCardMenu(strings, onSaveToPlaylist)
        }

        // The gap between cards, inside the card for the reason the feed's is:
        // the pressed fill has to reach the bottom of the text.
        Spacer(Modifier.height(Space.md))
    }
}

/**
 * One item, and only one.
 *
 * A card whose actions mean nothing draws no dot rather than a dead one — the
 * rule this app already follows — and for an upstream result exactly one action
 * means something. Paint rather than sampled glass, for [menuSurface]'s reasons.
 */
@Composable
private fun ExternalCardMenu(strings: Strings, onSaveToPlaylist: () -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        Icon(
            imageVector = MoreVertical,
            contentDescription = strings.moreOptions,
            tint = Tokens.text2,
            modifier = Modifier
                .size(Size.iconButton)
                .clip(CircleShape)
                .clickable { open = true }
                .padding(Space.sm),
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color.Transparent,
            shape = GlassRadius.panel,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.menuSurface(GlassRadius.panel),
        ) {
            DropdownMenuItem(
                text = { Text(strings.saveToPlaylist, color = Tokens.text) },
                onClick = { open = false; onSaveToPlaylist() },
            )
        }
    }
}
