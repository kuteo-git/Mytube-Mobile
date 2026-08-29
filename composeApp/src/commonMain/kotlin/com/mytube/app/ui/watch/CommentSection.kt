package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.Comment
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.avatarColourFor
import com.mytube.app.ui.home.formatRelative
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.theme.Tokens

/**
 * The comments, read-only.
 *
 * ## Why there is no "Add a comment" field
 *
 * The web app has one and the gateway can accept a post — and what it does is
 * write the comment into **this household's own catalogue**. It never reaches
 * YouTube. On the web that is defensible beside a keyboard; on a phone, a text
 * field under two thousand real YouTube comments reads as a reply to them, and
 * it would be a reply nobody outside this house will ever see. Building that
 * deliberately is a decision; putting a text field on a screen is not.
 *
 * ## Why replies are drawn and not folded away
 *
 * There is one level of them — YouTube nests no deeper — and the count is small.
 * A "show 3 replies" control would be machinery to hide three lines.
 */
@Composable
fun CommentSection(
    count: Int,
    comments: List<Comment>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Column(modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
        Text(
            text = "$count ${strings.comments}",
            color = Tokens.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Space.md))

        if (comments.isEmpty()) {
            // "None yet" and "still asking" are different answers, and saying
            // the first while the second is true is the fault this section is
            // most likely to show: the import takes a few seconds, and an empty
            // heading in the meantime reads as a video nobody has commented on.
            Text(
                text = if (loading) strings.checkChecking else strings.noComments,
                color = Tokens.text2,
                fontSize = 14.sp,
            )
            return@Column
        }

        comments.forEach { comment ->
            CommentRow(comment)
            comment.replies.forEach { reply ->
                // Indented by the avatar's width plus its gap, so a reply lines
                // up under the text it answers rather than under the picture.
                Row {
                    Spacer(Modifier.width(24.dp + Space.md))
                    CommentRow(reply, small = true)
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, small: Boolean = false) {
    val strings = LocalStrings.current
    val size = if (small) 20.dp else 24.dp

    Row(Modifier.fillMaxWidth().padding(bottom = Space.lg)) {
        // A coloured initial, not an image. The gateway sends an empty
        // `avatarPath` for every commenter — it stores no pictures for people
        // who are not channels — so an AsyncImage here would be a request per
        // comment for a 404, and a grid of grey circles.
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(avatarColourFor(comment.authorHandle)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = comment.authorHandle.trimStart('@').take(1).uppercase(),
                color = Color.White,
                fontSize = if (small) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorHandle,
                    color = Tokens.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = formatRelative(comment.publishedAt, strings),
                    color = Tokens.text2,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.text,
                color = Tokens.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}
