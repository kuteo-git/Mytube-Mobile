package com.mytube.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.mytube.app.ui.theme.Tokens

/**
 * A channel's picture, or its initial on a colour.
 *
 * ## Why the letter exists
 *
 * Every avatar in this app was an `AsyncImage` over a grey circle, and a grey
 * circle is what a channel with no picture — or one whose picture has not been
 * mirrored into the library yet — looked like for ever. On a feed of ten cards
 * that reads as ten images that failed to load, which is precisely the reading
 * the web app avoids: it draws the first letter on a colour derived from the
 * name, so a missing picture still identifies somebody.
 *
 * The colour is [avatarColourFor], the same function the top bar and the profile
 * picker already used — so one channel is one colour everywhere it appears,
 * rather than a different one per screen.
 *
 * ## Why Subcompose rather than an error painter
 *
 * `AsyncImage` takes a `Painter` for its error slot, and a letter centred on a
 * disc is not a painter without drawing text by hand. `SubcomposeAsyncImage`
 * takes composables, which is what this needs and what it costs a subcomposition
 * for.
 *
 * An empty `path` never reaches the network: it is not a load that fails, it is
 * a picture that was never claimed to exist, and asking for it would put a
 * failing request on the wire for every channel in the list.
 */
@Composable
fun ChannelAvatar(
    name: String,
    mediaBaseUrl: String,
    path: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val circle = modifier.size(size).clip(CircleShape)
    val model = if (path.isBlank()) null else imageModel(mediaBaseUrl, path)

    if (model == null) {
        Initial(name, size, circle)
        return
    }

    SubcomposeAsyncImage(
        model = model,
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = circle.background(Tokens.surface),
        // Both slots, not only `error`. A request still in flight over a grey
        // disc is the same picture as one that failed, and on a cold feed that
        // is most of what a reader sees.
        loading = { Initial(name, size, Modifier.fillMaxSize()) },
        error = { Initial(name, size, Modifier.fillMaxSize()) },
    )
}

/**
 * The letter on its colour.
 *
 * The glyph is sized from the disc rather than fixed, because the same avatar is
 * drawn at 24dp on a comment, 36 in a card, 40 on the watch page and 72 on a
 * channel — and one font size across that range is either lost or overflowing.
 */
@Composable
private fun Initial(name: String, size: Dp, modifier: Modifier) {
    Box(
        modifier.background(avatarColourFor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(),
            color = Color.White,
            fontSize = initialSize(size),
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Roughly 45% of the disc, which is where a single capital sits comfortably. */
private fun initialSize(size: Dp): TextUnit = (size.value * 0.45f).sp
