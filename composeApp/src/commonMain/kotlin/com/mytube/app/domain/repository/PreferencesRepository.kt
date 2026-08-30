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

    /**
     * How loud the voice is, as a fraction of the video's own level.
     *
     * Per device for the reason the whole file is: a phone held at arm's length
     * and a laptop on a desk need different answers, and neither is a statement
     * about taste. Above 1 is allowed — synthesised speech is quieter than film
     * audio, which is why the default is not simply "the same as the video".
     *
     * Two levels rather than one, and independent, which the web app learned the
     * hard way: chaining them meant the voice took its gain from the *already
     * ducked* video, so moving either control moved both and nobody could set
     * one without changing the other.
     */
    suspend fun voiceLevel(): Float

    suspend fun setVoiceLevel(level: Float)

    /** What the video drops to while a line is being read. */
    suspend fun duckLevel(): Float

    suspend fun setDuckLevel(level: Float)

    /**
     * The video this device had open when it was last closed, or empty.
     *
     * So that reopening the app puts the miniplayer back rather than dropping
     * somebody at the top of the feed with no sign of what they were half way
     * through. It is deliberately **not** a queue or a history — one id, the
     * one that was on screen.
     *
     * Where to resume *from* is not stored beside it: the server already knows,
     * in the video's own user state, and it is written every ten seconds of
     * playback. Keeping a second copy here would give two answers that can
     * disagree, and the one on the phone would be the one nothing else can see.
     */
    suspend fun lastVideoId(): String

    suspend fun setLastVideoId(id: String)
}
