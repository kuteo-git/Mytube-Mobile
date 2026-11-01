package com.mytube.app.domain.model

/** One line of a caption file, and when it is on screen. */
data class SubtitleCue(
    val startSeconds: Double,
    val endSeconds: Double,
    val text: String,
)

/**
 * WebVTT, parsed.
 *
 * ## Why this app parses captions at all
 *
 * On Android it does not: ExoPlayer side-loads the `.vtt` beside the video and
 * draws the cues itself. AVPlayer cannot be given a caption file that is not in
 * the HLS manifest, and this server's manifest carries none — the captions live
 * on disk as separate files, which is the shape the web app has always used. So
 * iOS either parses them here or shows nothing.
 *
 * **The alternative was adding a `SUBTITLES` group to the master playlist on the
 * server**, which is the native answer and would work for every HLS client at
 * once. It is deliberately not taken: hls.js on the web would then discover
 * caption tracks the web player does not know it has, and the household watches
 * on the web every day. A change that risks the working thing to fix the new
 * thing is the wrong way round.
 *
 * ## What this deliberately does not do
 *
 * No cue settings (`align`, `line`, `position`), no regions, no styling blocks,
 * no `<c>`/`<v>` markup beyond stripping tags. YouTube's caption files use none
 * of it, measured across this library, and a parser that half-implements a
 * feature is worse than one that plainly does not: the half that works teaches
 * people to trust the half that does not.
 */
fun parseWebVtt(body: String): List<SubtitleCue> {
    // A file that does not say WEBVTT is not one. The server already refuses to
    // write such a file — it once wrote XML into a `.vtt` and everything
    // downstream reported success — and refusing it here too costs one
    // comparison and removes a whole class of silent nonsense.
    if (!body.trimStart().startsWith("WEBVTT")) return emptyList()

    val cues = mutableListOf<SubtitleCue>()
    // Both line endings. A file fetched over HTTP may carry either, and
    // splitting on "\n" alone leaves a trailing "\r" that turns the last
    // timestamp of every line into an unparseable one.
    val lines = body.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    var i = 0
    while (i < lines.size) {
        val arrow = lines[i].indexOf("-->")
        if (arrow < 0) {
            i++
            continue
        }
        val start = parseTimestamp(lines[i].substring(0, arrow))
        // The end is followed by cue settings often enough to matter, so it is
        // taken up to the first space rather than to the end of the line.
        val rest = lines[i].substring(arrow + 3).trim()
        val end = parseTimestamp(rest.substringBefore(' '))
        i++
        if (start == null || end == null) continue

        val text = StringBuilder()
        // `isEmpty`, not `isBlank`, and the difference is the whole feature.
        // WebVTT ends a cue at an **empty** line, and YouTube's auto-generated
        // files put a line holding a single space directly under the timestamp.
        // Stopping on blank ended every cue before its first word, so a file
        // that parsed, served and looked perfect produced no captions at all.
        while (i < lines.size && lines[i].isNotEmpty()) {
            val line = decodeEntities(stripTags(lines[i]))
            i++
            // Skipped after stripping rather than before: a line that is only
            // inline timestamps becomes empty here and is not a line of speech.
            if (line.isEmpty()) continue
            if (text.isNotEmpty()) text.append('\n')
            text.append(line)
        }
        val body1 = text.toString().trim()
        // An empty cue is a gap, not a line. Keeping it would blank the screen
        // for its duration in a way that reads as the captions having stopped.
        //
        // The lower bound on length is for YouTube's rolling auto-captions:
        // every line is published twice, once over its real span and again as a
        // 10ms cue carrying the final wording. Measured on a real file, the
        // pair is `2.639 --> 6.540` and `6.540 --> 6.550` with identical text.
        // Kept, the second one is a flicker at the end of every line.
        if (body1.isNotEmpty() && end - start > 0.05) {
            cues.add(SubtitleCue(start, end, body1))
        }
    }
    return cues
}

/**
 * `HH:MM:SS.mmm` or `MM:SS.mmm`.
 *
 * The hours field is optional in the specification and YouTube omits it on
 * anything under an hour, so a parser that requires three parts silently drops
 * every cue of most videos.
 */
private fun parseTimestamp(raw: String): Double? {
    val parts = raw.trim().split(':')
    if (parts.size !in 2..3) return null
    val seconds = parts.last().replace(',', '.').toDoubleOrNull() ?: return null
    val minutes = parts[parts.size - 2].toIntOrNull() ?: return null
    val hours = if (parts.size == 3) parts[0].toIntOrNull() ?: return null else 0
    return hours * 3600.0 + minutes * 60.0 + seconds
}

/**
 * Turn the HTML escapes YouTube writes into the characters they stand for.
 *
 * A caption file is XML underneath, so a line beginning with a speaker marker
 * arrives as `&gt;&gt; That's BC MP Zoe Royer`. Drawn raw — which is what this
 * app did, and what a screenshot of the simulator caught — the viewer reads
 * `&gt;&gt;` over the picture, and an apostrophe in an English caption becomes
 * `&#39;` in the middle of a word.
 *
 * `&amp;` is decoded **last**, deliberately. Doing it first turns `&amp;gt;`
 * into `&gt;` and the next pass then turns that into `>`, so text that was
 * quoting an escape silently becomes the thing it was quoting.
 *
 * Only the handful that actually appear. A general entity table is a dependency
 * and a parser; this is a caption line, and the five below are what a caption
 * line carries.
 */
private fun decodeEntities(line: String): String {
    if (!line.contains('&')) return line
    return line
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}

/**
 * `<v Speaker>`, `<c.colour>`, `<00:01.000>` — all removed, text kept.
 *
 * Runs **before** [decodeEntities], and the order is load-bearing: decoding
 * first would turn a caption's literal `&lt;b&gt;` into `<b>`, which this would
 * then strip as markup — silently deleting text the file was quoting.
 */
private fun stripTags(line: String): String {
    val out = StringBuilder()
    var inTag = false
    for (c in line) {
        when {
            c == '<' -> inTag = true
            c == '>' -> inTag = false
            !inTag -> out.append(c)
        }
    }
    return out.toString().trim()
}

/**
 * The cue on screen at [seconds], or null.
 *
 * A linear scan, deliberately. A caption file for a long video holds a couple of
 * thousand cues and this is asked four times a second, so a binary search would
 * be the obvious optimisation — and the previous cue is nearly always the answer
 * or next to it, which is what the hint is for. Correct and boring beats clever
 * on the path that runs during playback.
 */
fun cueAt(cues: List<SubtitleCue>, seconds: Double): SubtitleCue? =
    cues.firstOrNull { seconds >= it.startSeconds && seconds < it.endSeconds }
