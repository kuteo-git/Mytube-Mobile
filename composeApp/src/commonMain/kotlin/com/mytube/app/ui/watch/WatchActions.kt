package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
 * **Share** is in the web app and is dropped for now rather than drawn: it needs
 * a platform share sheet on each side, and a button that opens nothing is the
 * one thing §5 of the server charter forbids outright.
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
    narrating: Boolean,
    /** Either the feature's name or how far the server has got with it. */
    narrationLabel: String,
    onReact: (Reaction) -> Unit,
    onToggleSaved: () -> Unit,
    onToggleNarration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        // Narration sits in this row rather than behind a gear, because it is
        // the reason somebody opened this app rather than the web one, and a
        // feature reached through a menu is a feature most people never find.
        //
        // It is **first**, against the convention that puts Like first, because
        // its label grows to carry the pass's progress — and in fourth place
        // that label ran off the edge of the phone, leaving "Đang chuẩn bị" with
        // the numbers cut off. A row that scrolls is not an excuse for hiding
        // the one control the screen is for.
        ActionPill(
            icon = if (narrating) SpeakerFilledIcon else SpeakerIcon,
            label = narrationLabel,
            active = narrating,
            onClick = onToggleNarration,
        )
        ActionPill(
            // Filled when it is on. The background cannot carry this: the two
            // surface tokens are six units apart, which is a hover difference
            // and invisible as a state — measured on the emulator, pressing Like
            // set the reaction on the server and looked like nothing happened.
            icon = if (video.reaction == Reaction.Like) ThumbFilledIcon else ThumbIcon,
            label = strings.like,
            active = video.reaction == Reaction.Like,
            onClick = { onReact(Reaction.Like) },
        )
        ActionPill(
            icon = if (video.reaction == Reaction.Dislike) ThumbFilledIcon else ThumbIcon,
            label = strings.dislike,
            active = video.reaction == Reaction.Dislike,
            // The same glyph turned over, which is what a thumbs-down is. Two
            // separately drawn paths would drift, and a pair that does not
            // mirror is visible side by side.
            flipped = true,
            onClick = { onReact(Reaction.Dislike) },
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
