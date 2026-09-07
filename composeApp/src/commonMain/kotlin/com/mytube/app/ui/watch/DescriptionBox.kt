package com.mytube.app.ui.watch

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDate
import com.mytube.app.ui.home.formatViews
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.glassControl
import com.mytube.app.ui.theme.Tokens

/**
 * Views · date, and the description under them.
 *
 * A rounded panel rather than loose text, copied from the web app, and the shape
 * is doing work: the description is the one block on this screen whose length is
 * out of the app's hands — some are a line, some are a page of links — and a
 * container that clamps to two lines and opens on a press is what stops one
 * video's screen looking nothing like another's.
 *
 * The whole box is the target while it is collapsed, not just the word at the
 * bottom. A two-line clamp with a small "more" underneath asks somebody to aim
 * at eleven pixels of text to read the thing they are already looking at.
 */
@Composable
fun DescriptionBox(
    video: Video,
    /**
     * Whether the whole text is showing.
     *
     * Hoisted, and that is the whole of a bug this used to have. It was
     * `remember(video.id)` **inside** this composable, and this composable is an
     * item of the watch page's `LazyColumn` — which disposes an item once it is
     * scrolled out of the viewport, taking every `remember` in it. Measured:
     * press "more", scroll down past the up-next rail, scroll back, and the box
     * is showing two lines and "...more" again.
     *
     * The same page already had the answer twice over. `commentsOpen` and
     * `expandedReplies` are `rememberSaveable`, declared above the list rather
     * than inside an item, which is why the comments section stays open through
     * exactly the same journey.
     */
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Column(
        modifier
            .fillMaxWidth()
            // Opens and closes at the same speed as the comments section and the
            // up-next rail, because `SECTION_SPRING` is the one spec all three
            // share — the comment on it says why: two things a thumb's width
            // apart that fold at different speeds read as two different
            // controls.
            //
            // `animateContentSize` rather than `AnimatedVisibility`, which is
            // what the other two use. Those fold content that is either there or
            // not; this one is the *same* text at two heights, and there is
            // nothing to enter or exit — `maxLines` changes and the box has to
            // travel between the two heights that produces.
            //
            // Before the modifier the box jumped from two lines to twenty in a
            // single frame, which reads as the page having been replaced rather
            // than opened.
            .animateContentSize(SECTION_SPRING)
            .glassControl(RoundedCornerShape(12.dp))
            .then(if (expanded) Modifier else Modifier.clickable(onClick = onToggleExpanded))
            .padding(Space.md),
    ) {
        Text(
            text = listOfNotNull(
                formatViews(video.viewCount, strings).takeIf { video.viewCount > 0 },
                formatDate(video.publishedAt, strings).takeIf { video.hasPublishedDate },
            ).joinToString("  •  "),
            color = Tokens.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        if (video.description.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = video.description,
                color = Tokens.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = if (expanded) strings.showLess else strings.showMore,
                color = Tokens.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onToggleExpanded),
            )
        }
    }
}
