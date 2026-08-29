package com.mytube.app.ui.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.NarrationStatus
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.theme.Tokens

/**
 * What the gear on the control bar opens.
 *
 * ## Why it sits under the picture and not in a sheet
 *
 * Because that is where the web app puts it, and the reason is the same on both:
 * these settings are about the video that is playing, and a sheet that covers
 * the video makes somebody change the narration setting while looking at a grey
 * rectangle. Anchored under the picture, the video keeps running above it.
 *
 * ## What is here, and what is not
 *
 * Narration, with the pass's progress under it — the two things this app can
 * actually do. **Subtitles and Autoplay are deliberately absent**: this app
 * renders no subtitle track and holds no queue, so a row for either would be the
 * dead button §5 of the server charter forbids. They belong here the day those
 * exist.
 */
@Composable
fun PlayerSettingsPanel(
    visible: Boolean,
    subtitles: List<SubtitleTrack>,
    subtitleLanguage: String,
    narrating: Boolean,
    narration: Narration,
    onSelectSubtitles: (String) -> Unit,
    onToggleNarration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Tokens.surface)
                .padding(horizontal = Space.lg, vertical = Space.md),
        ) {
            // Only when the video has any. A "Subtitles: Off" row over a video
            // with no tracks is a control whose every option is the state it is
            // already in.
            if (subtitles.isNotEmpty()) {
                Text(
                    text = strings.subtitles,
                    color = Tokens.text2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(Space.sm))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    TrackChip(strings.off, subtitleLanguage.isEmpty()) { onSelectSubtitles("") }
                    subtitles.forEach { track ->
                        TrackChip(
                            label = trackLabel(track),
                            selected = subtitleLanguage == track.language,
                        ) { onSelectSubtitles(track.language) }
                    }
                }
                Spacer(Modifier.height(Space.lg))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.narration,
                    color = Tokens.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Switch(
                    checked = narrating,
                    onCheckedChange = { onToggleNarration() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Tokens.brand,
                        uncheckedThumbColor = Tokens.text2,
                        uncheckedTrackColor = Tokens.surfaceHover,
                    ),
                )
            }

            // The progress the web app shows as "Preparing speech… 18/388". It
            // is here rather than on the button because a bar can say *how far*,
            // and a label on a pill can only say a number in a space that runs
            // off the edge of the phone.
            if (narrating && narration.total > 0) {
                Spacer(Modifier.height(Space.md))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (narration.isWorking) strings.narrationPreparing(
                            narration.done,
                            narration.total,
                        ) else strings.narration,
                        color = Tokens.text2,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "${narration.done}/${narration.total}",
                        color = Tokens.text2,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Tokens.line),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(narration.progress)
                            .fillMaxHeight()
                            .background(Tokens.brand),
                    )
                }
            }

            if (narration.status == NarrationStatus.Failed) {
                Spacer(Modifier.height(Space.sm))
                Text(strings.narrationFailed, color = Tokens.brand, fontSize = 12.sp)
            }
        }
    }
}


/**
 * One caption track in the row, or Off.
 *
 * The same shape as the feed's chips rather than a menu: there are rarely more
 * than three, and a row shows every option at once where a dropdown shows the
 * current one and hides the rest — which on this panel is the whole question.
 */
@Composable
private fun TrackChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Tokens.bg else Tokens.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Tokens.text else Tokens.surfaceHover)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}


/**
 * A caption track, named for a chip.
 *
 * The server's own label is a sentence — "Vietnamese (auto-generated)" — which
 * does not fit a control two words wide. What is left is the language and
 * whether a machine wrote it.
 *
 * The subtag is dropped. The machine track is tagged `vi-x-mt`, which the server
 * chose deliberately so nothing mistakes it for the human Vietnamese track a
 * video may also carry — that is a *storage* distinction, and printing it raw
 * gave a chip reading "VI-X-MT (auto)". The viewer's question is which language,
 * and "(auto)" already says the rest.
 */
internal fun trackLabel(track: SubtitleTrack): String {
    val language = track.language.substringBefore('-').uppercase()
    return if (track.generated) "$language (auto)" else language
}
