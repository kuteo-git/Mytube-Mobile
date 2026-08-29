package com.mytube.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The same colours the web app draws, taken from the one place they are written
 * down: `design-system/local-youtube/MASTER.md` in the server repository.
 *
 * ## Why copied rather than shared
 *
 * There is no mechanism to share a Markdown table between a TypeScript build and
 * a Gradle one, and inventing one for a dozen hex values would be a build step
 * to keep alive forever. What matters is that the *source* is single: when a
 * colour changes it changes there, and both clients are updated from it. That is
 * why every constant below names its token — a reader can find the row.
 *
 * ## Why the names are the web's and not Material's
 *
 * `surfaceHover` and `line` are not Material concepts, and translating them into
 * `surfaceVariant` and `outline` would mean two vocabularies for one design.
 * Material's scheme is filled in from these below, rather than the other way
 * round.
 */
object Tokens {
    /** `--bg` — page, top bar, sidebar. */
    val bg = Color(0xFF0F0F0F)

    /** `--surface` — dropdowns, chips, tooltips. */
    val surface = Color(0xFF212121)

    /** `--surface-hover` */
    val surfaceHover = Color(0xFF272727)

    /** `--surface-input` — the search field. */
    val surfaceInput = Color(0xFF121212)

    /** `--line` — borders and dividers. */
    val line = Color(0xFF303030)

    /** `--text` — titles, channel names. 17.9:1 against `bg`. */
    val text = Color(0xFFF1F1F1)

    /** `--text-2` — view counts, timestamps. */
    val text2 = Color(0xFFAAAAAA)

    /** `--link` — hashtags, URLs. */
    val link = Color(0xFF3EA6FF)

    /** `--brand` — the LIVE badge and the watched-progress bar. */
    val brand = Color(0xFFFF0000)

    /** `--invert-bg` — the Subscribe button, an active chip. */
    val invertBg = Color(0xFFF1F1F1)

    /** `--invert-text` */
    val invertText = Color(0xFF0F0F0F)

    /** `--ring` — focus. */
    val ring = Color(0xFF1C62B9)
}

/**
 * Dark only, and the system setting is not consulted.
 *
 * The web app has one palette and it is this one; a light theme would be a
 * second design nobody has drawn. Following the system would mean Material
 * inventing light colours that match nothing — worse than ignoring it.
 */
@Composable
fun MytubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Tokens.bg,
            onBackground = Tokens.text,
            surface = Tokens.surface,
            onSurface = Tokens.text,
            surfaceVariant = Tokens.surfaceHover,
            onSurfaceVariant = Tokens.text2,
            primary = Tokens.link,
            onPrimary = Tokens.invertText,
            error = Tokens.brand,
            outline = Tokens.line,
        ),
        content = content,
    )
}
