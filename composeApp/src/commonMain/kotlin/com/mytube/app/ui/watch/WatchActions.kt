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
import com.mytube.app.ui.shell.glassControl
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
        // Like and dislike are **one pill with a divider**. Two separate pills
        // is what this was, and beside the web app it reads as two unrelated
        // opinions rather than one control with two directions — which is what
        // they are: pressing either clears the other.
        //
        // **No count.** It carried the like count, because the web app's does
        // and because an empty space where a number belongs reads as a number
        // that failed to load. On a phone that reasoning loses to the row it is
        // in: this row scrolls sideways, and the widest thing in it was a figure
        // nobody presses. The count is a fact about *other people*, and it is
        // still on the page — under the title, beside the date — where facts
        // about the video live. What is left here is two thumbs, which is what
        // the control is for.
        Row(
            modifier = Modifier
                .glassControl(RoundedCornerShape(percent = 50)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillHalf(
                icon = if (video.reaction == Reaction.Like) ThumbFilledIcon else ThumbIcon,
                label = strings.like,
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
    onClick: () -> Unit,
    flipped: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            // 16 rather than 14, now that there is only a glyph between them.
            // The padding *is* the target here: a 20dp thumb with 14dp either
            // side was a 48dp half only because the count was making up the
            // width on one of them, and the two halves were different sizes.
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
            .glassControl(RoundedCornerShape(percent = 50), selected = !subscribed)
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
    // The content follows the surface.
    //
    // `glassControl(selected = true)` swaps to the app's **inverted** surface —
    // solid and light — because a state has to be a different kind of surface
    // rather than a slightly different shade of the same one. What that means
    // for anything drawn on it is that white ink disappears: Save in its saved
    // state was a blank white pill with an invisible bookmark and an invisible
    // word on it, which is how it was reported. Every selected surface in this
    // app owes its content the same swap.
    val ink = if (active) Tokens.invertText else Tokens.text

    Row(
        modifier = Modifier
            .glassControl(RoundedCornerShape(percent = 50), selected = active)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ink,
            modifier = Modifier
                .size(20.dp)
                .scale(scaleX = 1f, scaleY = if (flipped) -1f else 1f),
        )
        Spacer(Modifier.width(Space.sm))
        Text(label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(0.dp))
}
