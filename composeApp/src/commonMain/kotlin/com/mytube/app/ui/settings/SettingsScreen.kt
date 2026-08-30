package com.mytube.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.domain.repository.FeedMix
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.Language
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.ScreenTitle
import com.mytube.app.ui.shell.tabContentPadding
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The reduced settings this app carries.
 *
 * ## What is deliberately not here
 *
 * Storage, Activity, the feed mix, the ranking constants, the proxy, narration
 * and speech. Those are screens for *fixing the server*, and people fix a server
 * sitting in front of a computer — the web app has them and keeps them. Putting
 * them on a phone would be carrying six screens of machinery across for a case
 * that is better served by the machine they are already on.
 *
 * What is left is what belongs to this device: where its library is, and what
 * language it reads in.
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

            // Only once the server has answered. Sliders drawn at zero and then
            // jumping to their real values look like a setting that was reset.
            if (feedMix != null) {
                item(key = "mix-heading") { SectionHeading(strings.feedMix) }
                item(key = "mix") {
                    FeedMixSection(feedMix, onChangeMix)
                    Spacer(Modifier.height(Space.md))
                }
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
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        Text(label, color = Tokens.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = Tokens.text2,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
