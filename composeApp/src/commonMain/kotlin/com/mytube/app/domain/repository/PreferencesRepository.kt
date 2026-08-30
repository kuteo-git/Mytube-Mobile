package com.mytube.app.domain.repository

/**
 * What this device remembers about playing a video.
 *
 * ## Why these are per device and not per member
 *
 * The same reasoning the server charter gives for the equaliser: a phone in a
 * pocket and a television in a room want different answers, and none of this is
 * a statement about taste that the ranker should hear. Two people sharing a
 * profile on two phones should not fight over whether the voice is on.
 *
 * ## Why they are a port of their own
 *
 * `ServerRepository` answers "where is the library and who is asking" —
 * questions that must be settled before any request can be made. These are
 * answered *while watching*, and folding them in would leave that name true of
 * half its members.
 */
interface PreferencesRepository {

    /**
     * Whether the Vietnamese voice is wanted.
     *
     * Remembering this on is not free, and that is the point of saying so:
     * opening any video then starts a server pass — translation and speech for
     * every line. It is what somebody who turned it on asked for, and it is why
     * this is the one preference here with a cost.
     */
    suspend fun narration(): Boolean

    suspend fun setNarration(on: Boolean)

    suspend fun autoplay(): Boolean

    suspend fun setAutoplay(on: Boolean)

    /**
     * The caption language to show, or empty for none.
     *
     * A language, not "the first track". Remembering EN and being given VI on
     * the next video because that is what it happens to carry is not what was
     * asked for — a video without the remembered language opens with subtitles
     * off, which is the honest answer to "show me English" when there is none.
     */
    suspend fun subtitleLanguage(): String

    suspend fun setSubtitleLanguage(language: String)
}
