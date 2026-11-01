package com.mytube.app.domain.model

/**
 * One spoken line, and where it belongs in the video.
 *
 * The server does the work — reading the captions, translating them, synthesising
 * and fitting each line to the gap it has — and hands back this. That division is
 * the reason the app exists: a thousand text-to-speech requests from a
 * backgrounded phone is exactly the work an operating system suspends, while a
 * list of audio files with timestamps is ordinary playback.
 */
data class NarrationClip(
    val startSeconds: Double,
    /**
     * How long the line has, not how long its audio runs.
     *
     * The server fits the audio *into* this, so the two are close — but this is
     * the number ducking must use. Ducking has to end when the line's time is
     * up rather than when its file happens to stop, or a clip that came back a
     * fraction short leaves the video quiet after it.
     */
    val durationSeconds: Double,
    val clipUrl: String,
    val text: String,
    /**
     * When the line was spoken, as milliseconds since the epoch. `0` for a
     * recorded video.
     *
     * A broadcast has no zero to count from, so its lines cannot be placed by
     * an offset — see the charter. Both the video and the caption feed carry
     * `EXT-X-PROGRAM-DATE-TIME`, and both players can report the wall clock of
     * their own playhead, so the two meet on the clock instead.
     *
     * Absence is `0` and named rather than implied, the same rule every other
     * absent thing in `domain` follows.
     */
    val startsAtEpochMillis: Long = 0,
) {
    val endSeconds: Double get() = startSeconds + durationSeconds

    /** Whether this line is placed by the clock rather than by an offset. */
    val isLive: Boolean get() = startsAtEpochMillis != 0L

}

/**
 * How far the server has got.
 *
 * Progressive by design: a full video takes minutes, and the clips already
 * fitted are playable now. `clips` is what is ready, not what there will be.
 */
data class Narration(
    val status: NarrationStatus,
    val done: Int,
    val total: Int,
    val clips: List<NarrationClip>,
    val error: String = "",
) {
    val isWorking: Boolean get() = status == NarrationStatus.Running

    /** 0..1, or 0 before the server knows how many lines there are. */
    val progress: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    companion object {
        val Empty = Narration(NarrationStatus.Idle, 0, 0, emptyList())
    }
}

enum class NarrationStatus {
    /** No pass has been asked for. Not an error, and not "finished with none". */
    Idle,
    Running,
    Done,
    Failed,
    ;

    companion object {
        /**
         * Anything unrecognised is Idle.
         *
         * A word a later server release invents must not be drawn as a failure:
         * the honest reading of "I do not know this state" is that nothing has
         * happened yet, which is also the state that offers to start a pass.
         */
        fun fromWire(value: String): NarrationStatus = when (value) {
            "running" -> Running
            "done" -> Done
            "failed" -> Failed
            else -> Idle
        }
    }
}

/**
 * How loud the voice and the video are, given what the viewer set.
 *
 * Copied from the web app rather than reasoned out again, including the reason
 * the two are independent: they used to be chained, so the player ducked the
 * video and narration then took its gain from the *already ducked* figure —
 * a constant that read as "2.5× louder" was really 0.5× of master, and moving
 * one level dragged the other with it.
 *
 * A pure function so the relationship can be tested without an audio clock.
 */
data class NarrationLevels(val narration: Float, val video: Float)

/** The voice, as a fraction of master. Above 1 is allowed: speech is quieter than film audio. */
const val DEFAULT_VOICE_LEVEL = 0.5f

/** What the video drops to while the voice is speaking. */
const val DEFAULT_DUCK_LEVEL = 0.2f

/**
 * The ceiling both levels share: the sliders' end, and the clamp on a stored
 * value.
 *
 * 2.0 rather than 1.0 because the voice legitimately wants to be louder than
 * the source it is read over — the web app allows the same, and a limiter sits
 * after it. Applying the same ceiling to the duck level costs nothing and means
 * one number to reason about.
 */
const val MAX_LEVEL = 2f

fun levelsFor(
    master: Float,
    muted: Boolean,
    narrating: Boolean,
    narrationLevel: Float = DEFAULT_VOICE_LEVEL,
    duckLevel: Float = DEFAULT_DUCK_LEVEL,
): NarrationLevels = when {
    muted -> NarrationLevels(0f, 0f)
    !narrating -> NarrationLevels(0f, master)
    else -> NarrationLevels(master * narrationLevel, master * duckLevel)
}

/**
 * The clip that should be speaking at this moment, or null.
 *
 * A linear scan, deliberately. A three-hour video is a few thousand clips and
 * this runs four times a second; a binary search would be faster and would be
 * one more thing that can be wrong about a list the server appends to while it
 * is being read.
 */
fun clipAt(clips: List<NarrationClip>, seconds: Double): NarrationClip? =
    clips.firstOrNull { seconds >= it.startSeconds && seconds < it.endSeconds }


/**
 * The first clip that begins after this moment, or null.
 *
 * What the player buffers ahead of time. Read from the playhead rather than
 * from the clip now speaking, so a seek lands on the right answer without the
 * scheduler having to notice that one happened — the same reasoning that makes
 * the whole loop poll rather than schedule.
 */
fun nextClipAfter(clips: List<NarrationClip>, seconds: Double): NarrationClip? =
    clips.firstOrNull { it.startSeconds > seconds }

/**
 * The language spoken in a video, as far as narration cares: Vietnamese, the
 * language it speaks, or anything else. Unknown when the server could not
 * say — YouTube had no automatic captions, refused, or the title gave
 * nothing away.
 */
enum class SpokenLanguage { Vietnamese, Other, Unknown }

/**
 * Whether a video opens narrated: the switch on, and the video not speaking
 * Vietnamese — its narration would read its Vietnamese captions over its own
 * voice. Not known is narrated, as the switch says. Pressing the switch on a
 * Vietnamese video still narrates it, and is remembered: this is only where a
 * video starts.
 */
fun narratesOnOpen(preferred: Boolean, spoken: SpokenLanguage): Boolean =
    preferred && spoken != SpokenLanguage.Vietnamese
