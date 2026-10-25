package com.mytube.app.ui.tv

import com.mytube.app.ui.home.WatchedBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mytube.app.ui.home.BadgeBackground
import com.mytube.app.ui.home.OpeningOverlay
import com.mytube.app.ui.home.Radius
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.formatDuration
import com.mytube.app.ui.shell.MenuAction
import com.mytube.app.ui.shell.pressable
import com.mytube.app.ui.shell.rememberMenuAnchor
import com.mytube.app.ui.theme.Tokens

/**
 * A video's picture on a television, and the thing focus lands on.
 *
 * The one place the focus target, its rim and everything drawn over a thumbnail
 * live — the shelf card, the list row and the playlist tile all draw it. Those
 * were the pieces tuned on the television one measurement at a time, and two
 * copies of them is how one copy stops being tuned.
 *
 * The words under or beside a picture are read, not aimed at, so the rim goes
 * round the picture alone, the way YouTube's TV app draws it.
 */
@Composable
fun TvThumbnail(
    /** What the picture loads: a resolved library path or an upstream URL; empty for none. */
    thumbnail: String,
    title: String,
    durationSeconds: Int,
    watchedFraction: Double,
    /** Being written into the catalogue: the picture says so and takes no press. */
    opening: Boolean,
    onClick: () -> Unit,
    /**
     * What a long press of OK offers — the phone card's dots, which a remote
     * has no way to aim at. Empty for no menu. **No default**: a card that
     * forgets it is a video nobody can save from the sofa.
     */
    menu: List<MenuAction>,
    modifier: Modifier = Modifier,
    /** Drawn in the middle when there is no picture — a playlist with nothing in it. */
    placeholder: ImageVector? = null,
) {
    val (anchor, show) = rememberMenuAnchor()
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .then(if (menu.isEmpty()) Modifier else anchor)
            // Before the clip, so the rise is the picture's and the rim is not
            // shaved by the corner it follows.
            .pressable(
                enabled = !opening,
                rimShape = RoundedCornerShape(Radius.thumbnail),
                onLongClick = if (menu.isEmpty()) null else ({ show(menu) }),
                onClick = onClick,
            )
            .clip(RoundedCornerShape(Radius.thumbnail))
            // Only where no picture will cover it. Under every loaded image it
            // was one more layer of the screen's largest surface, painted on
            // every frame — the shelves showed red in HWUI's overdraw view.
            .then(if (thumbnail.isEmpty()) Modifier.background(Tokens.surface) else Modifier),
    ) {
        if (thumbnail.isNotEmpty()) {
            AsyncImage(
                model = thumbnail,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                // The surface until the picture arrives, or if it never does.
                placeholder = ColorPainter(Tokens.surface),
                error = ColorPainter(Tokens.surface),
                modifier = Modifier.fillMaxSize(),
            )
        } else if (placeholder != null) {
            Icon(
                imageVector = placeholder,
                contentDescription = null,
                tint = Tokens.text2,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }
        if (durationSeconds > 0) {
            Text(
                text = formatDuration(durationSeconds),
                color = Tokens.text,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Space.sm)
                    .clip(RoundedCornerShape(Radius.badge))
                    .background(BadgeBackground)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        // The phone card's red hairline, for the phone card's reason: only a
        // video somebody has actually started.
        if (watchedFraction > 0.01) {
            WatchedBar(watchedFraction)
        }
        if (opening) OpeningOverlay()
    }
}
