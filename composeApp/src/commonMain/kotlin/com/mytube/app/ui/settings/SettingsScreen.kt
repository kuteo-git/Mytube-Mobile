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
import com.mytube.app.ui.shell.GlassSlider
import com.mytube.app.ui.shell.GlassTextField
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
    onOpenProfile: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenLanguage: () -> Unit,
    onPickLanguage: (Language) -> Unit,
    onChangeMix: (FeedMix) -> Unit,
    /** How loud the voice is, as a fraction of the video's own level. */
    voiceLevel: Float = DEFAULT_VOICE_LEVEL,
    /** What the video drops to while a line is being read. */
    duckLevel: Float = DEFAULT_DUCK_LEVEL,
    /** The household's speech voice, or empty when the server did not answer. */
    voice: String = "",
    /** Who this device is watching as, or empty before the server has answered. */
    profileName: String = "",
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

            // Who is watching. It was the avatar in the top bar, which is where
            // YouTube puts it and where it was wrong for this app: that corner
            // is the most-looked-at part of the screen and this is a question
            // answered about once a month, in a household of four people who all
            // know which one they are. Here it states its own answer — the row
            // reads the member's name without anybody pressing it.
            item(key = "profile") {
                SettingRow(
                    label = strings.settingsProfile,
                    value = profileName.ifEmpty { strings.profileTitle },
                    onClick = onOpenProfile,
                )
            }

            // History, which was a tab. See [Tab]: what earns a place on the
            // bottom bar is what you move between while browsing, and this is a
            // list you open to find one video again. It sits directly above
            // Saved because the two are the same kind of thing — a shelf this
            // device keeps.
            item(key = "history") {
                SettingRow(
                    label = strings.historyTitle,
                    value = strings.historyDetail,
                    onClick = onOpenHistory,
                )
            }

            // One row, not two. The saved shelf did not disappear — it is the
            // first row *on* the playlists page, which is where it belongs once
            // there is more than one collection to keep things in.
            item(key = "playlists") {
                SettingRow(
                    label = strings.playlists,
                    value = strings.playlistsDetail,
                    onClick = onOpenPlaylists,
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

            item(key = "voice") {
                SettingRow(
                    label = strings.narration,
                    // The voice's own name, or what to do about not having one.
                    // The row states the answer; the screen behind it holds the
                    // three controls that set it.
                    value = voice.ifEmpty { strings.voiceNameHint },
                    onClick = onOpenVoice,
                )
            }

            item(key = "language") {
                SettingRow(
                    label = strings.settingsLanguage,
                    // Named in its own words, on the row as well as in the list
                    // behind it. Somebody who set the wrong one is looking at an
                    // interface they cannot read, and this is the line that says
                    // which one they are in.
                    value = when (language) {
                        Language.English -> strings.languageEnglish
                        Language.Vietnamese -> strings.languageVietnamese
                    },
                    onClick = onOpenLanguage,
                )
            }
        }
    }
}

private const val MAX_LEVEL = 2f

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
            onOpenProfile = {},
            onOpenHistory = {},
            onOpenVoice = {},
            onOpenLanguage = {},
            onOpenPlaylists = {},
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
            onOpenProfile = {},
            onOpenHistory = {},
            onOpenVoice = {},
            onOpenLanguage = {},
            onOpenPlaylists = {},
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
            onOpenProfile = {},
            onOpenHistory = {},
                onOpenVoice = {},
                onOpenLanguage = {},
                onOpenPlaylists = {},
                onPickLanguage = {},
                onChangeMix = {},
            )
        }
    }
}

