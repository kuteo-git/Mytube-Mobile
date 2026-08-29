package com.mytube.app.ui.home

import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.i18n.TimeUnit
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The numbers on a card.
 *
 * Their own file, and pure. They were in the screen and moved out the moment a
 * preview block was edited and took one of them with it — a function that any
 * screen may need has no business living inside one of them.
 *
 * Every rule here is copied from the web app's `format.ts` rather than
 * reinvented, so a household member switching between the phone and the browser
 * sees the same number written the same way.
 */

/**
 * `12:34`, and `1:02:03` past an hour.
 *
 * No language parameter, and that is not an oversight: a duration is digits and
 * colons in both languages this app speaks.
 */
fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
    return if (hours > 0) "$hours:$mm:${seconds.toString().padStart(2, '0')}"
    else "$mm:${seconds.toString().padStart(2, '0')}"
}

/**
 * `4.1M` in English, `4.1Tr` in Vietnamese.
 *
 * The suffix comes from [Strings] rather than a table in here, because that is
 * precisely where the web app's version went wrong — its formatters carried
 * English grammar into a language with no plural and printed "3 ngàys trước".
 * The unit belongs to the language, so the language supplies it.
 */
fun formatCount(n: Long, strings: Strings): String = when {
    n >= 1_000_000_000 -> trim(n / 1_000_000_000.0) + strings.billionSuffix
    n >= 1_000_000 -> trim(n / 1_000_000.0) + strings.millionSuffix
    n >= 1_000 -> trim(n / 1_000.0) + strings.thousandSuffix
    else -> n.toString()
}

/** `157K views` / `157N lượt xem`. */
fun formatViews(n: Long, strings: Strings): String =
    "${formatCount(n, strings)} ${strings.views}"

/**
 * `1 day ago` / `1 ngày trước`, from an ISO timestamp.
 *
 * [Strings] supplies the whole phrase rather than a word to slot into one
 * template: English marks the past before the unit, Vietnamese after it.
 *
 * Empty for a date in the future or one that cannot be read — a card then shows
 * the views alone rather than "in -1 days".
 */
fun formatRelative(iso: String, strings: Strings, now: Instant = Clock.System.now()): String {
    val published = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val seconds = (now - published).inWholeSeconds
    if (seconds < 0) return ""
    for (unit in TimeUnit.entries) {
        if (seconds >= unit.seconds) {
            return strings.relative((seconds / unit.seconds).toInt(), unit)
        }
    }
    return strings.justNow
}

/** One decimal below a hundred, none at or above it, and never a trailing `.0`. */
private fun trim(value: Double): String {
    if (value >= 100) return value.roundToLong().toString()
    val oneDecimal = (value * 10).roundToLong() / 10.0
    val whole = oneDecimal.toLong()
    return if (oneDecimal == whole.toDouble()) whole.toString() else oneDecimal.toString()
}

/**
 * Where an image actually lives.
 *
 * A thumbnail is either a path inside the library — served by the gateway under
 * `/media` — or an address upstream. The channel page is the second case: it
 * lists a channel's uploads by asking YouTube rather than the catalogue, because
 * a scan only ever brings in the newest few dozen, so most of what it returns
 * has never been near this disk and carries an `i.ytimg.com` URL.
 *
 * Deciding it here rather than at each `AsyncImage` means a card does not have
 * to know which kind of list it is in, and one rule cannot disagree with itself
 * across three call sites.
 */
fun imageModel(mediaBaseUrl: String, path: String): String =
    if (path.startsWith("http://") || path.startsWith("https://")) path
    else "${mediaBaseUrl.trimEnd('/')}/media/$path"
