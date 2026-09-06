package com.mytube.app.ui.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.Comment
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.avatarColourFor
import com.mytube.app.ui.home.formatRelative
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.pressableGlassControl
import com.mytube.app.ui.shell.rememberGlassPress
import com.mytube.app.ui.shell.skeletonShade
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The avatar's width plus its gap: where a comment's text starts. */
private val REPLY_INDENT = 24.dp + Space.md

/**
 * The comments, read-only, as rows of the page's own scroller.
 *
 * ## Why the whole section is one item, after being one item per comment
 *
 * It was split so that "a `Column` of two thousand comments" would not compose
 * at once. **There is no such column.** The gateway's handler reads
 * `intParam(r, "pageSize", 20)` and this app sends no page size and no page
 * token, so what arrives is twenty top-level comments and their replies — every
 * time, for every video.
 *
 * That number is what decides the shape here, because one item is the only way
 * the section can open and close the way the replies inside it do:
 * `AnimatedVisibility` animates a node, and there is no node spanning several
 * items of a `LazyColumn`. Split across items, the rows simply cease to exist
 * between one frame and the next — measured, and the whole of the reported
 * fault.
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
 * ## Why replies are folded away now, when they were not
 *
 * They were drawn open, on the reasoning that one level nests shallowly and "a
 * *show 3 replies* control would be machinery to hide three lines". That is true
 * of a comment with three replies and false of the ones that have forty: the
 * top-level comments — the thing somebody came to read — end up separated by
 * pages of answers to the one above.
 *
 * @param expanded the ids of the comments whose replies are showing. Held by the
 *   caller rather than here, because whether a thread is open outlives the row —
 *   and because folding the section away must not silently unfold every thread
 *   in it.
 */
fun LazyListScope.commentSection(
    comments: List<Comment>,
    loading: Boolean,
    loaded: Boolean,
    open: Boolean,
    onToggleOpen: () -> Unit,
    expanded: Set<String>,
    onToggleReplies: (String) -> Unit,
) {
    item(key = "comments-heading") {
        CommentsHeading(
            // The number is what has been counted, and nothing has been counted
            // until the section has been opened once: the gateway sends no
            // comment count with a video, so there is no figure to print before
            // the list itself arrives.
            count = if (loaded) comments.size else -1,
            open = open,
            onClick = onToggleOpen,
        )
    }

    item(key = "comments-body") {
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(SECTION_SPRING) + fadeIn(),
            exit = shrinkVertically(SECTION_SPRING) + fadeOut(),
        ) {
            // 4dp here and not 12: the heading row already carries 8dp of
            // padding below its pane, so this is what makes the gap under the
            // pane **12pt measured on the screen** — the same as the gap under
            // the up-next rail's own header. Two sections a thumb apart opening
            // into different amounts of space is what a page looks like when
            // each half was spaced by eye.
            Column(Modifier.padding(top = Space.xs)) {
                when {
                    loading -> CommentSkeleton()
                    comments.isEmpty() -> Text(
                        text = LocalStrings.current.noComments,
                        color = Tokens.text2,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = Space.lg),
                    )
                    else -> comments.forEach { comment ->
                        CommentThread(
                            comment = comment,
                            expanded = comment.id in expanded,
                            onToggleReplies = { onToggleReplies(comment.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * How a section on this page opens and closes.
 *
 * One spec, shared by the comments and by the up-next rail below them: two
 * sections a thumb's width apart that fold at different speeds read as two
 * different controls. The replies inside a comment use it too.
 */
val SECTION_SPRING = spring<IntSize>(stiffness = Spring.StiffnessMediumLow)

/**
 * The heading, which is also the control that folds the section away.
 *
 * The same shape as the up-next rail's own header a section below it — a whole
 * row that takes the press, with a chevron at its end that turns. Two sections
 * on one page that fold and unfold must not be two different controls.
 *
 * The section starts folded, and that is what makes the fetch worth deferring: a
 * video is opened to be watched, and the comments under it are a second thing
 * somebody decides to read. Until they do, this app was asking the gateway for
 * two thousand of them — and, on a video nobody had opened, asking it to scrape
 * them from YouTube first.
 *
 * @param count the number to print, or -1 while there is nothing counted yet.
 */
@Composable
private fun CommentsHeading(count: Int, open: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "comments-chevron")

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm)
            .pressableGlassControl(RoundedCornerShape(12.dp), onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count < 0) strings.comments else "$count ${strings.comments}",
            color = Tokens.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = ChevronIcon,
            contentDescription = strings.comments,
            tint = Tokens.text,
            modifier = Modifier.size(20.dp).rotate(turn),
        )
    }
}

/**
 * One comment and, folded under it, its replies.
 *
 * The replies live in the same list item as the comment they answer, rather than
 * being items of their own. Two reasons, and the second is the one that decided
 * it: a comment's replies are few where the comments themselves are many, and
 * `AnimatedVisibility` inside one item genuinely unfolds — whereas separate
 * items can only be animated with `animateItem`, which slides them into place.
 * Sliding is not what was asked for and is not what the control says will happen.
 */
@Composable
private fun CommentThread(
    comment: Comment,
    expanded: Boolean,
    onToggleReplies: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
        CommentRow(comment)

        if (comment.replies.isNotEmpty()) {
            Row {
                Spacer(Modifier.width(REPLY_INDENT))
                RepliesToggle(comment.replies.size, expanded, onToggleReplies)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(SECTION_SPRING) + fadeIn(),
                exit = shrinkVertically(SECTION_SPRING) + fadeOut(),
            ) {
                Column(Modifier.padding(top = Space.md)) {
                    comment.replies.forEach { reply ->
                        // Indented by the avatar's width plus its gap, so a
                        // reply lines up under the text it answers rather than
                        // under the picture.
                        Row {
                            Spacer(Modifier.width(REPLY_INDENT))
                            CommentRow(reply, small = true)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.lg))
    }
}

/**
 * "5 replies" with a chevron, and "Hide replies" once they are.
 *
 * Flat text rather than a pane, matching the web app's own — `text-link` there,
 * which is [Tokens.link] here. That leaves it the one control in the app with
 * nothing to squash, so what answers the finger is the text itself dimming: the
 * same pair of springs [rememberGlassPress] drives everywhere else, read as an
 * alpha instead of a scale. A control with no acknowledgement is one people
 * press twice, and the chevron's turn arrives only after the finger has lifted.
 *
 * The chevron is on the right, which is not where the web app puts it. On a page
 * this wide it is a disclosure row, and a disclosure row's mark sits at its end.
 */
@Composable
private fun RepliesToggle(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val source = remember { MutableInteractionSource() }
    val press = rememberGlassPress(source)
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "replies-chevron")

    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = Space.sm, vertical = 6.dp)
            // Read in a draw lambda, so a press costs a redraw and no
            // recomposition — the reason `pressSquish` takes one too.
            .alpha(1f - PRESS_DIM * press.fraction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) strings.hideReplies else strings.replyCount(count),
            color = Tokens.link,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(Space.xs))
        Icon(
            imageVector = ChevronIcon,
            contentDescription = null,
            tint = Tokens.link,
            modifier = Modifier.size(16.dp).rotate(turn),
        )
    }
}

/** How far the label fades under a finger. */
private const val PRESS_DIM = 0.45f

/**
 * Comments, before they arrive.
 *
 * The section used to say "checking" in a line of grey text, which is a sentence
 * where every other loading screen in this app draws the shape of what is
 * coming. Three rows rather than five: comments sit at the bottom of the page,
 * and more grey only lengthens a region nobody has scrolled to yet.
 */
@Composable
private fun CommentSkeleton() {
    val shade = skeletonShade()
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
        repeat(3) {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.lg)) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(shade))
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Bar(shade, 0.35f)
                    Spacer(Modifier.height(Space.sm))
                    Bar(shade, 0.9f)
                    Spacer(Modifier.height(6.dp))
                    // Shorter, so three of these read as paragraphs rather than
                    // as a table.
                    Bar(shade, 0.6f)
                }
            }
        }
    }
}

@Composable
private fun Bar(shade: Color, width: Float) {
    Box(
        Modifier
            .fillMaxWidth(width)
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(shade),
    )
}

@Composable
private fun CommentRow(comment: Comment, small: Boolean = false) {
    val strings = LocalStrings.current
    val size = if (small) 20.dp else 24.dp

    Row(Modifier.fillMaxWidth().padding(bottom = if (small) Space.lg else Space.sm)) {
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

// --- previews ---------------------------------------------------------------
//
// Of the section rather than of the whole watch screen: whether it is folded is
// a `remember` inside that screen, so a preview of it could only ever draw the
// state the screen starts in.

private fun previewComment(id: String, handle: String, text: String, replies: Int = 0) = Comment(
    id = id,
    authorHandle = handle,
    avatarPath = "",
    text = text,
    publishedAt = "2026-09-05T09:00:00Z",
    likeCount = 12,
    replies = List(replies) {
        Comment(
            id = "$id-r$it",
            authorHandle = "@replier$it",
            avatarPath = "",
            text = "That is exactly what I thought watching it.",
            publishedAt = "2026-09-05T11:00:00Z",
            likeCount = 1,
        )
    },
)

private val previewComments = listOf(
    previewComment("1", "@teolucnguyen", "The second half of this is the best thing all week.", replies = 3),
    previewComment("2", "@haiyen", "Anyone got a link to the original report?"),
    previewComment("3", "@mnguyen", "Nghe kỹ đoạn 12:04, chỗ đó mới là điểm chính.", replies = 1),
)

@Composable
private fun previewSection(
    comments: List<Comment> = emptyList(),
    loading: Boolean = false,
    loaded: Boolean = false,
    open: Boolean = true,
    expanded: Set<String> = emptySet(),
) {
    MytubeTheme {
        Box(Modifier.background(Tokens.bg)) {
            LazyColumn {
                commentSection(
                    comments = comments,
                    loading = loading,
                    loaded = loaded,
                    open = open,
                    onToggleOpen = {},
                    expanded = expanded,
                    onToggleReplies = {},
                )
            }
        }
    }
}

/** How a video opens: folded, with nothing fetched and so no number to print. */
@Preview
@Composable
private fun CommentsFoldedPreview() = previewSection(open = false)

/** Pressed once, and the request is out. */
@Preview
@Composable
private fun CommentsLoadingPreview() = previewSection(loading = true)

/** Answered, replies folded away behind their buttons. */
@Preview
@Composable
private fun CommentsPreview() = previewSection(comments = previewComments, loaded = true)

/** One thread open, which is the state the whole control exists for. */
@Preview
@Composable
private fun CommentsExpandedPreview() =
    previewSection(comments = previewComments, loaded = true, expanded = setOf("1"))

/** Answered with nothing, which is not the same as never asked. */
@Preview
@Composable
private fun CommentsEmptyPreview() = previewSection(loaded = true)

/** Vietnamese, where "Ẩn phản hồi" is the longer of the two labels. */
@Preview
@Composable
private fun CommentsVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        previewSection(comments = previewComments, loaded = true, expanded = setOf("3"))
    }
}
