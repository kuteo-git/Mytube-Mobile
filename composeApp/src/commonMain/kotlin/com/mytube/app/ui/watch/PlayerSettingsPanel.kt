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
import androidx.compose.material3.SwitchDefaults
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.unit.Dp
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.GlassSheet
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.ui.shell.glassControl
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.shell.pressableGlassControl

/**
 * What the gear on the control bar opens.
 *
 * ## Why it is a bottom sheet
 *
 * It was anchored directly under the picture, which pushed the whole page down
 * as it opened — the title and the channel row moved under a thumb already
 * reaching for them. A sheet rises over the page and leaves it where it was, and
 * on a phone the bottom is where a hand is.
 *
 * The video keeps playing above it either way. That was the requirement, and a
 * sheet meets it as long as it does not cover the picture — which is why this one
 * is not full height and never will be: everything in it is a setting *about*
 * what is on screen.
 *
 * ## What is here, and what is not
 *
 * Subtitles, narration and autoplay — the three things that are about *this*
 * video and are changed while it plays.
 *
 * The first two arrived once this app could actually render a track and follow a
 * queue; an earlier note here recorded their absence as deliberate, on the
 * ground that a row for something the app could not do is the dead button §5 of
 * the server charter forbids. That reasoning was right and its premise is gone.
 *
 * How loud the voice is and which voice it is live in Settings, not here. See
 * the note at their old position below.
 */
@Composable
fun BoxScope.PlayerSettingsPanel(
    visible: Boolean,
    /**
     * What the sheet's glass blurs: the watch page it opens over.
     *
     * Passed in rather than taken from `LocalHaze` — that one is the shell's,
     * registered on the tab content, and the watch screen is a sibling drawn
     * over it. A sheet reading the ambient state would frost the feed hiding
     * behind the video instead of the page the settings belong to.
     */
    backdrop: LayerBackdrop?,
    /** How far up the bottom of the screen the sheet must clear. */
    bottomInset: Dp,
    subtitles: List<SubtitleTrack>,
    subtitleLanguage: String,
    /**
     * Whether narration can be offered for what is playing.
     *
     * A pass reads a caption track and speaks it, so the answer is no wherever
     * there is no track to read: every broadcast, and any recorded video the
     * library holds no captions for. A switch that turns on and stays at
     * nothing is the dead control §5 of the server charter refuses. The row is
     * absent rather than disabled: a disabled switch invites a second press.
     */
    canNarrate: Boolean,
    narrating: Boolean,
    narration: Narration,
    autoplay: Boolean,
    onSelectSubtitles: (String) -> Unit,
    onToggleNarration: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current

    // Glass, in the scene — see [GlassSheet] for why Material's sheet could never
    // be frosted here, and why that is a fact about popup layers rather than a
    // parameter nobody found.
    GlassSheet(
        visible = visible,
        backdrop = backdrop,
        // No scrim over the picture. Dimming the window would grey out the video
        // these settings are about.
        scrim = Color.Transparent,
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // A gap under the drag handle. The content began immediately
                // below it, so the first heading sat against the grabber and the
                // sheet read as cropped rather than as a sheet with a top.
                .padding(
                    start = Space.lg,
                    end = Space.lg,
                    top = Space.md,
                    // The navigation inset **plus** the usual gap. Material's
                    // sheet added the inset itself; this one is an ordinary
                    // child of the screen, so the home indicator is the
                    // caller's to clear.
                    bottom = Space.xl + bottomInset,
                ),
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

            if (canNarrate) {
                SwitchRow(
                    label = strings.narration,
                    checked = narrating,
                    onToggle = onToggleNarration,
                )

                Spacer(Modifier.height(Space.md))
            }
            SwitchRow(
                label = strings.autoplay,
                checked = autoplay,
                onToggle = onToggleAutoplay,
            )

            // The voice's levels and its name are **not** here.
            //
            // They were, briefly, and they belong in Settings: they are chosen
            // once for the device — and, in the voice's case, once for the whole
            // house — while everything else on this sheet is about the video
            // playing above it. A control somebody sets once does not earn a
            // place on the surface they open twenty times an evening.
            //
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
            .pressableGlassControl(
                RoundedCornerShape(percent = 50),
                selected = selected,
                onClick = onClick,
            )
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


@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Tokens.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        // UIKit's switch on iOS, Material's on Android. A Material toggle on an
        // iPhone is the single most obvious tell that an app is not native, and
        // this one sits beside the two words a viewer reads most often.
        AdaptiveSwitch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Tokens.brand,
                uncheckedThumbColor = Tokens.text2,
                uncheckedTrackColor = Tokens.surfaceHover,
            ),
        )
    }
}

