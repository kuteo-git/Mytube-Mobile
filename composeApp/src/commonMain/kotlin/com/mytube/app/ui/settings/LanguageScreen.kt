package com.mytube.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.Language
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.DetailScaffold
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens

/**
 * Which language the app reads in.
 *
 * Two rows, on a screen of their own rather than at the bottom of Settings — the
 * list is short enough to have looked harmless there and long enough to push the
 * things above it out of sight on a small phone.
 */
@Composable
fun LanguageScreen(
    language: Language,
    onPick: (Language) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    DetailScaffold(strings.settingsLanguage, onBack, strings.back) {
        Language.entries.forEach { option ->
            LanguageRow(
                // Named in its own words, always. Somebody who pressed the wrong
                // row is looking at an interface they cannot read, and "English"
                // written in English is the way back out.
                label = when (option) {
                    Language.English -> strings.languageEnglish
                    Language.Vietnamese -> strings.languageVietnamese
                },
                selected = option == language,
                onClick = { onPick(option) },
            )
        }
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
        Text(label, color = Tokens.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        // A tick on the chosen row and nothing on the other, which is how every
        // list of this kind on iOS marks its choice — and how the Settings app
        // itself does. It replaced a pair of dots, one lit and one grey: two
        // marks for one answer, and the unlit one reads as a second, disabled
        // option rather than as "not this".
        if (selected) {
            Icon(
                imageVector = TickIcon,
                contentDescription = null,
                tint = Tokens.brand,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Shared with [ProfileScreen], which answers a question of the same shape. */
internal val TickIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Tick",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(9f, 16.2f)
            lineTo(4.8f, 12f)
            lineTo(3.4f, 13.4f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineTo(19.6f, 5.6f)
            close()
        }
    }.build()
}

@Preview
@Composable
private fun LanguagePreview() {
    MytubeTheme {
        LanguageScreen(language = Language.Vietnamese, onPick = {}, onBack = {})
    }
}
