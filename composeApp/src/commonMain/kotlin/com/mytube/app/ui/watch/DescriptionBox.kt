package com.mytube.app.ui.watch

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun DescriptionBox(video: Video, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    var expanded by remember(video.id) { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .glassControl(RoundedCornerShape(12.dp))
            .then(if (expanded) Modifier else Modifier.clickable { expanded = true })
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
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}
