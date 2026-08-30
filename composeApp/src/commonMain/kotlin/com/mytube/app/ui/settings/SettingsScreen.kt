package com.mytube.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.model.DEFAULT_DUCK_LEVEL
import com.mytube.app.domain.model.DEFAULT_VOICE_LEVEL
import com.mytube.app.domain.repository.FeedMix
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.Language
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.ScreenTitle
import com.mytube.app.ui.shell.tabContentPadding
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import kotlin.math.roundToInt
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The reduced settings this app carries.
 *
 * ## What is deliberately not here
 *
 * Storage, Activity, the feed mix, the ranking constants and the proxy. Those
 * are screens for *fixing the server*, and people fix a server sitting in front
 * of a computer — the web app has them and keeps them. Putting them on a phone
 * would be carrying six screens of machinery across for a case that is better
 * served by the machine they are already on.
 *
 * What is left is what belongs to this device: where its library is, what
 * language it reads in, and how the Vietnamese voice sounds.
 *
 * ## Why the voice is here and not on the player
 *
 * It was on the player's own sheet first, beside the switch that turns the voice
 * on — which put it one tap from where it is heard, and made it a control
 * somebody scrolls past every time they open that sheet to change a subtitle
 * track. These are set once: how loud the reader is against the film, and which
 * voice reads. Once-a-month settings belong on the once-a-month screen.
 *
 * The two levels are this device's; **the voice is the household's**, because it
 * lives on the server that synthesises the speech. Changing it changes the
 * television too, and stops every clip already made from being reused. That is
 * worth knowing and is why they are not presented as one group of three.
 */
@Composable
fun SettingsScreen(
    baseUrl: String,
    language: Language,
    /** Null until the server has answered, or if it cannot. */
    feedMix: FeedMix?,
    onOpenServer: () -> Unit,
    onOpenSaved: () -> Unit,
    onPickLanguage: (Language) -> Unit,
    onChangeMix: (FeedMix) -> Unit,
    /** How loud the voice is, as a fraction of the video's own level. */
    voiceLevel: Float = DEFAULT_VOICE_LEVEL,
    /** What the video drops to while a line is being read. */
    duckLevel: Float = DEFAULT_DUCK_LEVEL,
    /** The household's speech voice, or empty when the server did not answer. */
    voice: String = "",
    onVoiceLevel: (Float) -> Unit = {},
    onDuckLevel: (Float) -> Unit = {},
    onVoice: (String) -> Unit = {},
) {
    val strings = LocalStrings.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = tabContentPadding(),
        ) {
            item(key = "title") { ScreenTitle(strings.navSettings) }

            item(key = "server") {
                SettingRow(
                    label = strings.settingsServer,
                    // The address itself, not "Configured". Somebody opening
                    // this is checking *which* machine, usually because it
                    // stopped answering.
                    value = baseUrl.ifEmpty { strings.setTheAddress },
                    onClick = onOpenServer,
                )
            }

            item(key = "saved") {
                SettingRow(
                    label = strings.savedTitle,
                    value = strings.noSavedDetail,
                    onClick = onOpenSaved,
                )
            }

            // The Home feed mix is deliberately absent here.
            //
            // It is one setting for the whole household, three sliders that
            // divide a page between shares whose meaning takes a paragraph to
            // explain, and the server charter's own advice for tuning it is to
            // read `/api/feed/explain` first. That is a thing to do sitting at
            // the machine, like Storage and Activity — both of which this app
            // leaves on the web for the same reason.

            item(key = "narration-heading") {
                SectionHeading(strings.narration)
            }

            item(key = "voice-level") {
                LevelRow(strings.voiceLevel, voiceLevel, onVoiceLevel)
            }

            item(key = "duck-level") {
                LevelRow(strings.videoLevelWhileSpeaking, duckLevel, onDuckLevel)
            }

            item(key = "voice-name") {
                VoiceField(voice, onVoice)
            }

            item(key = "language-heading") {
                SectionHeading(strings.settingsLanguage)
            }

            items(Language.entries.size) { index ->
                val option = Language.entries[index]
                LanguageRow(
                    // Named in its own words, always. Somebody who pressed the
                    // wrong row is looking at an interface they cannot read, and
                    // "English" written in English is the way back out.
                    label = when (option) {
                        Language.English -> strings.languageEnglish
                        Language.Vietnamese -> strings.languageVietnamese
                    },
                    selected = option == language,
                    onClick = { onPickLanguage(option) },
                )
            }
        }
    }
}

/**
 * One level, named, with its figure beside it.
 *
 * The number is shown as a percentage because that is what the control means —
 * a fraction of the video's own volume — and because a slider with no readout
 * cannot be returned to a setting somebody liked. It goes above 100%:
 * synthesised speech is quieter than film audio and the voice legitimately needs
 * to sit over it.
 *
 * Continuous rather than stepped. This is a level matched by ear, and a notched
 * control makes the one setting that sounds right unreachable.
 */
@Composable
private fun LevelRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Tokens.text, fontSize = 14.sp)
            Text("${(value * 100).roundToInt()}%", color = Tokens.text2, fontSize = 13.sp)
        }
        // Material's slider on both. `AdaptiveSlider` exists on Calf's main
        // branch and is **not in the published 0.8.0** — checked by compiling
        // against it, not by reading the docs, which list it. The switch and the
        // sheet are there and are used; this one waits for a release that
        // carries it.
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..MAX_LEVEL,
            colors = SliderDefaults.colors(
                thumbColor = Tokens.brand,
                activeTrackColor = Tokens.brand,
                inactiveTrackColor = Tokens.surfaceHover,
            ),
        )
    }
}

/**
 * The voice the speech service is asked for, typed rather than chosen.
 *
 * A list would be right for exactly one provider. The gateway's own note says
 * why: OpenAI publishes no endpoint that lists voices, every service imitating
 * its API brings its own names, and a menu would refuse a voice that exists the
 * day a provider adds one.
 *
 * Committed when the field is finished with, not on every keystroke. This is a
 * *server* setting shared with every screen in the house, and each save stops
 * the clips already synthesised from being reused — sending one per letter typed
 * would throw that cache away eight times to change a voice once.
 */
@Composable
private fun VoiceField(voice: String, onVoice: (String) -> Unit) {
    val strings = LocalStrings.current
    // Keyed on what the server said, so a voice that arrives after this screen
    // is drawn replaces an untouched field rather than being ignored.
    var typed by remember(voice) { mutableStateOf(voice) }

    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        singleLine = true,
        label = { Text(strings.voiceName, fontSize = 13.sp) },
        supportingText = { Text(strings.voiceNameHint, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onVoice(typed.trim()) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Tokens.text,
            unfocusedTextColor = Tokens.text,
            focusedBorderColor = Tokens.brand,
            unfocusedBorderColor = Tokens.line,
            focusedLabelColor = Tokens.text2,
            unfocusedLabelColor = Tokens.text2,
            cursorColor = Tokens.brand,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm)
            // Leaving the field is finishing with it, the same as pressing Done.
            // Without this a voice typed and then left behind by a tap elsewhere
            // is typed and lost, which reads as the setting refusing to save.
            .onFocusChanged { if (!it.isFocused && typed.trim() != voice) onVoice(typed.trim()) },
    )
}

/** The ceiling both sliders share. Above 1 on purpose — see [LevelRow]. */
private const val MAX_LEVEL = 2f

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        color = Tokens.text2,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(
            start = Space.lg,
            end = Space.lg,
            top = Space.lg,
            bottom = Space.xs,
        ),
    )
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    // A chevron and a fill while pressed, because nothing else here says this
    // is a button. Two rows down there is a language list whose rows are also
    // tappable and look identical, so a row that opens a whole screen and a row
    // that sets a value were indistinguishable until you pressed one.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (pressed) Tokens.surfaceHover else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Tokens.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = Tokens.text2,
                fontSize = 12.sp,
                // Two lines, not one. The saved-videos description was cut off
                // mid-sentence on a phone, and a description that stops before
                // it has said anything is worse than no description.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.md))
        Icon(
            imageVector = ChevronRightIcon,
            contentDescription = null,
            tint = Tokens.text2,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The chevron every row that opens another screen carries. */
private val ChevronRightIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ChevronRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2f) {
            moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
        }
    }.build()

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Tokens.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        // A filled dot rather than a tick or a background: the row is a choice
        // between two, and the lit state has to be visible against tokens six
        // units apart — the lesson the Like button already cost.
        Spacer(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (selected) Tokens.brand else Tokens.surfaceHover),
        )
    }
}

// --- previews ---------------------------------------------------------------

@Preview
@Composable
private fun SettingsPreview() {
    MytubeTheme {
        SettingsScreen(
            baseUrl = "http://192.168.1.42:8180",
            language = Language.English,
            feedMix = FeedMix(60, 20, 20, fixedPercent = 28),
            onOpenServer = {},
            onOpenSaved = {},
            onPickLanguage = {},
            onChangeMix = {},
        )
    }
}

/** Nothing configured, which is what a fresh install shows. */
@Preview
@Composable
private fun SettingsUnconfiguredPreview() {
    MytubeTheme {
        SettingsScreen(
            baseUrl = "",
            language = Language.English,
            feedMix = null,
            onOpenServer = {},
            onOpenSaved = {},
            onPickLanguage = {},
            onChangeMix = {},
        )
    }
}

@Preview
@Composable
private fun SettingsVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        MytubeTheme {
            SettingsScreen(
                baseUrl = "http://192.168.1.42:8180",
                language = Language.Vietnamese,
                feedMix = FeedMix(60, 20, 20, fixedPercent = 28),
                onOpenServer = {},
                onOpenSaved = {},
                onPickLanguage = {},
                onChangeMix = {},
            )
        }
    }
}
