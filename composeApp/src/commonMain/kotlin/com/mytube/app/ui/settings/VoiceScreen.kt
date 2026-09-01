package com.mytube.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.DetailScaffold
import com.mytube.app.ui.shell.GlassSlider
import com.mytube.app.ui.shell.GlassTextField
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * How the Vietnamese voice sounds, on a screen of its own.
 *
 * It used to be three rows in the middle of Settings, between a shelf and a list
 * of languages. Two sliders and a text field are not a row in a menu — they are
 * a subject — and putting them inline made the settings page a page of controls
 * rather than a list of answers.
 *
 * **The two levels belong to this device; the voice belongs to the household.**
 * The levels are how loud the reader is *here*, and they are stored here. The
 * voice lives on the server that synthesises the speech, so changing it changes
 * the television too and stops every clip already made from being reused. That
 * is why the screen says so under the field rather than presenting all three as
 * one group.
 */
@Composable
fun VoiceScreen(
    voiceLevel: Float,
    duckLevel: Float,
    voice: String,
    onVoiceLevel: (Float) -> Unit,
    onDuckLevel: (Float) -> Unit,
    onVoice: (String) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    DetailScaffold(strings.narration, onBack, strings.back) {
        Level(strings.voiceLevel, voiceLevel, onVoiceLevel)
        Level(strings.videoLevelWhileSpeaking, duckLevel, onDuckLevel)

        Spacer(Modifier.height(Space.lg))

        // Keyed on what the server said, so a voice that arrives after this
        // screen is drawn replaces an untouched field rather than being ignored.
        var typed by remember(voice) { mutableStateOf(voice) }

        Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
            Text(strings.voiceName, color = Tokens.text2, fontSize = 13.sp)
            Spacer(Modifier.height(Space.xs))
            GlassTextField(
                value = typed,
                onValueChange = { typed = it },
                placeholder = strings.voiceNameHint,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Leaving the field is finishing with it, the same as pressing
                // Done. Without this a voice typed and then left behind by a tap
                // elsewhere is typed and lost, which reads as the setting
                // refusing to save.
                modifier = Modifier.onFocusChanged {
                    if (!it.isFocused && typed.trim() != voice) onVoice(typed.trim())
                },
            )
            Spacer(Modifier.height(Space.xs))
            Text(strings.voiceNameHint, color = Tokens.text2, fontSize = 12.sp)
        }
    }
}

/**
 * One level, named, with its figure beside it.
 *
 * The number is shown as a percentage because that is what the control means — a
 * fraction of the video's own volume — and because a slider with no readout
 * cannot be returned to a setting somebody liked. It goes above 100%:
 * synthesised speech is quieter than film audio and the voice legitimately needs
 * to sit over it.
 *
 * Continuous rather than stepped. This is a level matched by ear, and a notched
 * control makes the one setting that sounds right unreachable.
 */
@Composable
private fun Level(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Tokens.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${(value * 100).roundToInt()}%", color = Tokens.text2, fontSize = 13.sp)
        }
        GlassSlider(value = value, onValueChange = onChange, valueRange = 0f..MAX_LEVEL)
    }
}

/** The ceiling both sliders share. Above 1 on purpose — see [Level]. */
private const val MAX_LEVEL = 2f

@Preview
@Composable
private fun VoicePreview() {
    MytubeTheme {
        VoiceScreen(
            voiceLevel = 1.2f,
            duckLevel = 0.3f,
            voice = "alloy",
            onVoiceLevel = {},
            onDuckLevel = {},
            onVoice = {},
            onBack = {},
        )
    }
}

/** The one that matters: Vietnamese copy is longer, and these labels are long. */
@Preview
@Composable
private fun VoiceVietnamesePreview() {
    MytubeTheme {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalStrings provides VietnameseStrings,
        ) {
            VoiceScreen(
                voiceLevel = 1f,
                duckLevel = 0.25f,
                voice = "",
                onVoiceLevel = {},
                onDuckLevel = {},
                onVoice = {},
                onBack = {},
            )
        }
    }
}
