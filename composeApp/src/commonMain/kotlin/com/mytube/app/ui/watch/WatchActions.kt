package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.theme.Tokens

/**
 * Like · Dislike · Save, under the video.
 *
 * ## What is deliberately not here
 *
 * **Narration moved to the gear on the control bar**, where the web app keeps
 * it. As a pill its label had to carry the pass's progress, and a label that
 * grows in a row that scrolls is a label that runs off the edge of the phone.
 *
 * **Watch later** is absent for a different reason — it is a read-only mirror of
 * the member's YouTube account, and nothing in this system writes to it.
 *
 * The row scrolls sideways because it will grow, and a fourth pill on a narrow
 * phone otherwise clips silently.
 */
@Composable
fun WatchActions(
    video: Video,
    onReact: (Reaction) -> Unit,
    onToggleSaved: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        // Like and dislike are **one pill with a divider**, and the like carries
        // its count. Two separate pills is what this was, and beside the web app
        // it reads as two unrelated opinions rather than one control with two
        // directions — which is what they are: pressing either clears the other.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Tokens.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillHalf(
                icon = if (video.reaction == Reaction.Like) ThumbFilledIcon else ThumbIcon,
                label = strings.like,
                // The count sits beside the thumb, as it does on the web. Zero
                // is drawn rather than hidden: an empty space where a number
                // belongs reads as a number that failed to load.
                trailing = video.likeCount.toString(),
                onClick = { onReact(Reaction.Like) },
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Tokens.line),
            )
            PillHalf(
                icon = if (video.reaction == Reaction.Dislike) ThumbFilledIcon else ThumbIcon,
                label = strings.dislike,
                trailing = "",
                flipped = true,
                onClick = { onReact(Reaction.Dislike) },
            )
        }

        ActionPill(
            icon = ShareIcon,
            label = strings.share,
            active = false,
            onClick = onShare,
        )

        ActionPill(
            icon = if (video.saved) SaveFilledIcon else SaveIcon,
            // The label states what the button *did*, not what it will do:
            // "Saved" beside a filled bookmark. A control whose label flips to
            // the opposite verb makes people read it twice to work out which
            // state they are in.
            label = if (video.saved) strings.savedVideo else strings.saveVideo,
            active = video.saved,
            onClick = onToggleSaved,
        )
    }
}

/**
 * One end of the like/dislike pill.
 *
 * Its own composable rather than a flag on [ActionPill], because it has no
 * background and no rounding of its own — the pill around it owns both, which is
 * what makes the two halves read as one control.
 */
@Composable
private fun PillHalf(
    icon: ImageVector,
    label: String,
    trailing: String,
    onClick: () -> Unit,
    flipped: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Tokens.text,
            modifier = Modifier
                .size(20.dp)
                .scale(scaleX = 1f, scaleY = if (flipped) -1f else 1f),
        )
        if (trailing.isNotEmpty()) {
            Spacer(Modifier.width(Space.sm))
            Text(trailing, color = Tokens.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Subscribe, on the channel row.
 *
 * Filled while not subscribed and quiet once subscribed, which is the way round
 * every app draws it: the loud state is the invitation, not the confirmation.
 */
@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current

    Text(
        text = if (subscribed) strings.subscribed else strings.subscribe,
        color = if (subscribed) Tokens.text else Tokens.bg,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (subscribed) Tokens.surfaceHover else Tokens.text)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    flipped: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (active) Tokens.surfaceHover else Tokens.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Tokens.text,
            modifier = Modifier
                .size(20.dp)
                .scale(scaleX = 1f, scaleY = if (flipped) -1f else 1f),
        )
        Spacer(Modifier.width(Space.sm))
        Text(label, color = Tokens.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(0.dp))
}
