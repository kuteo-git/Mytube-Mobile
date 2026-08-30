package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.cueAt

/**
 * The captions, drawn by this app.
 *
 * Only reached where the player does not draw them itself — which today means
 * iOS, because AVPlayer will not take a caption file that is not in the HLS
 * manifest and this server's manifest carries none. `VideoPlayer.rendersSubtitles`
 * is what decides, so the two can never both draw.
 *
 * ## Why it sits above the controls in the stack and below them on the screen
 *
 * The control bar covers the bottom of the picture, which is exactly where
 * captions go. Raising the words when the controls are showing was considered
 * and rejected: text that jumps as the bar fades is harder to read than text
 * that stays still, and the controls hide after a few seconds anyway. It is
 * placed above the video and below the bar, so the bar wins the overlap for the
 * seconds it is visible.
 */
@Composable
fun SubtitleOverlay(
    cues: List<SubtitleCue>,
    positionSeconds: Double,
    modifier: Modifier = Modifier,
) {
    // Nothing at all rather than an empty box: a transparent view over the
    // picture is one more thing between a finger and the play/pause tap.
    val cue = cueAt(cues, positionSeconds) ?: return

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = cue.text,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                // A panel behind the words, not a shadow on them. Captions land
                // on whatever the video happens to be showing, and white text
                // over a bright frame is unreadable however it is outlined.
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
