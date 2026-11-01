package com.mytube.app.domain.model

/**
 * How a video can be played right now.
 *
 * The server offers several tiers — a file on disk, HLS, a muxed stream, a live
 * broadcast — and this app takes **the two that are HLS**: the ordinary tier and
 * the live one. That is a decision from the charter, not a limitation: HLS is
 * the one tier every platform here plays natively, and a media playlist *is* an
 * index, so seeking works without any of the machinery an unindexed stream
 * needs. The local file and the muxed stream are deliberately not taken.
 *
 * A sealed type rather than a nullable URL, because "not playable" has several
 * genuinely different meanings and each one wants a different sentence on the
 * screen. Collapsing them into null would leave the watch screen guessing.
 */
sealed interface Stream {

    /** Ready to play. */
    data class Playable(
        val url: String,
        /** The rung the server started the ladder at; hls.js and AVPlayer move from it. */
        val height: Int,
        /**
         * A broadcast still on air.
         *
         * It plays through the same code — the URL ends `.m3u8` like any other
         * tier — and it differs in the two things a player draws: it has no
         * length, and it has no end. Everything that would otherwise divide by
         * a duration has to ask this first.
         */
        val isLive: Boolean = false,
        /**
         * Whether this broadcast publishes captions, and so can be narrated.
         *
         * Only meaningful beside [isLive], and reported by the server rather
         * than guessed here — it genuinely varies between streams. A control
         * drawn without knowing this is one that fails on press for half of
         * them, which §5 of the server charter refuses outright.
         */
        val hasLiveCaptions: Boolean = false,
        /** The language of those captions, so the track can be named rather than labelled "Subtitles". */
        val liveCaptionsLanguage: String = "",
    ) : Stream

    /**
     * A broadcast that has not begun.
     *
     * Distinct from [Unavailable] because nothing is wrong and the answer
     * changes on its own — so the screen says "it will start on its own" and
     * keeps asking, rather than offering a retry that means nothing.
     */
    data object Upcoming : Stream

    /**
     * Upstream will not hand this over: members-only, private, removed.
     *
     * Permanent, so the screen names the reason and offers no retry.
     */
    data class Unavailable(val reason: String) : Stream

    /**
     * The server answered, and offered no tier this app can play.
     *
     * Not an error: the charter's own answer for a video whose upstream formats
     * cannot be described is "no tier and no error".
     */
    data object NothingPlayable : Stream
}
