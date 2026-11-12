package com.mytube.app.ui.watch

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.GlassItem
import com.mytube.app.ui.shell.GlassPane
import com.mytube.app.ui.shell.GlassPaneShape

/**
 * Like, dislike, save and share in one pane of the player's glass, for
 * upright fullscreen, where the page that holds them is out of sight.
 * The same four presses as [WatchActions], with the player's glyphs.
 */
@Composable
internal fun PortraitActions(
    video: Video,
    onReact: (Reaction) -> Unit,
    onToggleSaved: () -> Unit,
    onShare: () -> Unit,
    touched: () -> Unit,
) {
    val strings = LocalStrings.current
    val liked = video.reaction == Reaction.Like
    val disliked = video.reaction == Reaction.Dislike
    GlassPane("player-actions", GlassPaneShape.Capsule) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionItem(
                id = "like",
                symbol = if (liked) "hand.thumbsup.fill" else "hand.thumbsup",
                icon = if (liked) ThumbFilledIcon else ThumbIcon,
                label = strings.like,
                onPress = { onReact(Reaction.Like); touched() },
            )
            ActionItem(
                id = "dislike",
                symbol = if (disliked) "hand.thumbsdown.fill" else "hand.thumbsdown",
                icon = if (disliked) ThumbFilledIcon else ThumbIcon,
                label = strings.dislike,
                flipped = true,
                onPress = { onReact(Reaction.Dislike); touched() },
            )
            ActionItem(
                id = "save",
                symbol = if (video.saved) "bookmark.fill" else "bookmark",
                icon = if (video.saved) SaveFilledIcon else SaveIcon,
                label = if (video.saved) strings.savedVideo else strings.saveVideo,
                onPress = { onToggleSaved(); touched() },
            )
            ActionItem(
                id = "share",
                symbol = "square.and.arrow.up",
                icon = ShareIcon,
                label = strings.share,
                onPress = { onShare(); touched() },
            )
        }
    }
}

@Composable
private fun ActionItem(
    id: String,
    symbol: String,
    icon: ImageVector,
    label: String,
    onPress: () -> Unit,
    flipped: Boolean = false,
) {
    GlassItem(id = id, symbol = symbol, onPress = onPress) {
        ControlButton(
            icon = icon,
            label = label,
            onClick = onPress,
            modifier = if (flipped) Modifier.scale(scaleX = 1f, scaleY = -1f) else Modifier,
        )
    }
}
