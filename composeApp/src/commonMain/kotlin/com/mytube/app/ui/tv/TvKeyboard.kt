package com.mytube.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.shell.pressable
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.home.stroke

/**
 * One key of the television's on-screen keyboard.
 *
 * YouTube's TV app types with one of these rather than the system keyboard,
 * and for a reason that holds here: the system keyboard on a Google TV opens
 * over the page, hides the results it is meant to be filling, and on the
 * household's Chromecast asked to be typed on from a phone instead.
 */
sealed interface TvKey {
    data class Letter(val char: Char) : TvKey
    /** A Vietnamese tone mark, put on the last word — see [withTone]. */
    data class Mark(val tone: Tone) : TvKey
    data object Backspace : TvKey
    data object Space : TvKey
    data object Clear : TvKey
    /** Done typing: focus goes to the results. */
    data object Search : TvKey
    /** Letters ⇄ digits and symbols. */
    data object Symbols : TvKey
    /** English letters ⇄ Vietnamese letters and tone marks. */
    data object Language : TvKey
}

/** Which set of keys is showing. */
enum class KeyboardLayout { Latin, Vietnamese, Symbols }

/**
 * The query after one key.
 *
 * Letters are typed lower-case whatever the key shows — a search matches either
 * way, and a query that shouts is harder to read back. Space is never doubled
 * and never leads, so a stray press does not change what is asked. Keys that
 * are about the keyboard rather than the text leave it alone.
 */
internal fun typed(text: String, key: TvKey): String = when (key) {
    is TvKey.Letter -> text + key.char.lowercaseChar()
    is TvKey.Mark -> withTone(text, key.tone)
    TvKey.Backspace -> text.dropLast(1)
    TvKey.Space -> if (text.isEmpty() || text.endsWith(' ')) text else "$text "
    TvKey.Clear -> ""
    TvKey.Search, TvKey.Symbols, TvKey.Language -> text
}

/**
 * The grid of character keys for [layout], row by row.
 *
 * Seven across, in alphabetical order, as YouTube's TV app lays its own out —
 * QWERTY is a shape for ten fingers, and a remote reaches a key by walking to
 * it, so the order that is easiest to find wins. The Vietnamese layout is the
 * same letters with the seven that have their own diacritic and the five tone
 * marks under them.
 */
internal fun keyboardRows(layout: KeyboardLayout): List<List<TvKey>> {
    fun letters(row: String) = row.map { TvKey.Letter(it) }
    val latin = listOf("abcdefg", "hijklmn", "opqrstu", "vwxyz-'").map(::letters)
    return when (layout) {
        KeyboardLayout.Latin -> latin
        KeyboardLayout.Vietnamese -> latin + listOf(
            letters("ăâđêôơư"),
            Tone.entries.map { TvKey.Mark(it) },
        )
        KeyboardLayout.Symbols -> listOf("1234567", "890&.,?", "!@#()_+").map(::letters)
    }
}

/** The layout the language key leads to from [current]. */
internal fun otherLanguage(current: KeyboardLayout): KeyboardLayout =
    if (current == KeyboardLayout.Vietnamese) KeyboardLayout.Latin else KeyboardLayout.Vietnamese

/**
 * The keyboard: the grid, a column of keys beside it, and a row under it.
 *
 * [firstKey] is the top-left key's requester, so the screen can put focus on
 * the keyboard as it opens.
 */
@Composable
fun TvKeyboard(
    layout: KeyboardLayout,
    strings: Strings,
    firstKey: FocusRequester,
    onKey: (TvKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                keyboardRows(layout).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                        row.forEachIndexed { index, key ->
                            CharKey(
                                key = key,
                                onClick = { onKey(key) },
                                modifier = if (rowIndex == 0 && index == 0) {
                                    Modifier.focusRequester(firstKey)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                KeyBox(width = SIDE_KEY_WIDTH, onClick = { onKey(TvKey.Backspace) }) {
                    Icon(BackspaceIcon, strings.keyBackspace, tint = Tokens.text, modifier = Modifier.size(24.dp))
                }
                // Named in characters, not words: what the key switches to is
                // the same in every language.
                KeyLabel(if (layout == KeyboardLayout.Symbols) "ABC" else "&123", width = SIDE_KEY_WIDTH) {
                    onKey(TvKey.Symbols)
                }
                KeyLabel(if (layout == KeyboardLayout.Vietnamese) "VI" else "EN", width = SIDE_KEY_WIDTH) {
                    onKey(TvKey.Language)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            WideKey(strings.keySpace) { onKey(TvKey.Space) }
            WideKey(strings.clear) { onKey(TvKey.Clear) }
            WideKey(strings.search) { onKey(TvKey.Search) }
        }
    }
}

@Composable
private fun CharKey(key: TvKey, onClick: () -> Unit, modifier: Modifier) {
    val label = when (key) {
        is TvKey.Letter -> key.char.uppercaseChar().toString()
        // The tone shown on an a, which is how it is taught and how it is
        // recognised from across a room — from the one table that holds them.
        is TvKey.Mark -> withTone("a", key.tone)
        else -> ""
    }
    KeyLabel(label, modifier, onClick = onClick)
}

@Composable
private fun KeyLabel(
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = KEY_SIZE,
    onClick: () -> Unit,
) {
    KeyBox(modifier, width, onClick) {
        Text(
            text = label,
            color = Tokens.text,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A key's target: a plain square, as YouTube's keys are — the rim says which. */
@Composable
private fun KeyBox(
    modifier: Modifier = Modifier,
    width: Dp = KEY_SIZE,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(width, KEY_SIZE).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Space, clear and search: words on a pill, under the grid. */
@Composable
private fun WideKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(KEY_SIZE)
            .pressable(rimShape = RoundedCornerShape(Space.sm), onClick = onClick)
            .background(Tokens.surface, RoundedCornerShape(Space.sm))
            .padding(horizontal = Space.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Tokens.text,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A key: 40dp, YouTube's keyboard's pitch less the gap. */
private val KEY_SIZE = 40.dp
private val KEY_GAP = 4.dp

/** The keys beside the grid, wide enough for "&123" on one line. */
private val SIDE_KEY_WIDTH = 72.dp

/** A delete key's mark: a left-pointing tag with a cross in it. */
private val BackspaceIcon: ImageVector by lazy {
    stroke("Backspace") {
        moveTo(9f, 5f); lineTo(20f, 5f)
        curveTo(21.1f, 5f, 22f, 5.9f, 22f, 7f); lineTo(22f, 17f)
        curveTo(22f, 18.1f, 21.1f, 19f, 20f, 19f); lineTo(9f, 19f)
        lineTo(2f, 12f); close()
        moveTo(12f, 9f); lineTo(18f, 15f)
        moveTo(18f, 9f); lineTo(12f, 15f)
    }
}
