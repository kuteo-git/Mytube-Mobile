package com.mytube.app.domain.player

import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.DEFAULT_DUCK_LEVEL
import com.mytube.app.domain.model.DEFAULT_VOICE_LEVEL
import com.mytube.app.domain.model.clipAt
import com.mytube.app.domain.model.nextClipAfter
import com.mytube.app.domain.model.levelsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What a platform must provide for narration, and nothing more.
 *
 * ExoPlayer and AVPlayer share no type, so *something* has to be written twice.
 * This is the smallest thing that has to be: six calls with no decisions in
 * them. Everything that decides — when a line is due, how far the video ducks,
 * what happens on a pause or a seek — is in [Narrator] below, written once.
 */
interface NarrationHost {

    /** The video is actually playing, not paused, buffering or ended. */
    val videoIsPlaying: Boolean

    /** Where the video has got to. */
    val videoPositionSeconds: Double

    /** The video's current level, read once when narration starts. */
    fun videoVolume(): Float

    fun setVideoVolume(level: Float)

    /**
     * Fetch and buffer a clip that is not due yet, so [speak] can start it
     * without waiting.
     *
     * The server fits each line's audio to the gap before the next line, so a
     * clip's slot has no room to spare — and a clip that takes a moment to load
     * loses exactly that moment off its tail when the next one replaces it.
     * Measured as every line ending a fraction early.
     *
     * Called repeatedly with the same address; a platform that already holds it
     * must do nothing.
     */
    fun prepare(url: String)

    /** Begin one clip. Anything already speaking is replaced. */
    fun speak(url: String, volume: Float)

    /**
     * Change the level of the clip already speaking.
     *
     * Separate from [speak] because the obvious alternative — calling `speak`
     * again with the new level — restarts the clip, so dragging the voice slider
     * would make the current sentence begin again on every frame of the drag.
     * Does nothing when nothing is speaking.
     */
    fun setSpeechVolume(level: Float)

    /** Stop speaking and let go of the clip. */
    fun silence()
}

/**
 * The second voice.
 *
 * One short clip at a time, started from where the video has got to, with the
 * video ducked under it. The platform supplies [NarrationHost]; this holds every
 * decision, so Android and iOS cannot drift apart in behaviour — only in the API
 * calls underneath.
 *
 * ## Why a clip is not queued behind the video
 *
 * A clip has to begin at a moment *inside* the video. A playlist plays things
 * one after another and has no way to say that.
 *
 * ## Why it polls rather than schedules
 *
 * A timer per clip has to be cancelled and rebuilt on every seek, every pause,
 * and every list update — and the server's pass grows the list while it plays.
 * Four checks a second against the playhead cannot drift, needs no special case
 * for seeking, and picks up a clip appended a moment ago on the next tick. A
 * line starting a quarter-second late is inaudible as an error; a rebuild on
 * every seek is a bug waiting for someone to drag the bar.
 */
class Narrator(
    private val host: NarrationHost,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {

    private val scope = scope
    private var ticker: Job? = null
    private var clips: List<NarrationClip> = emptyList()

    /** What is speaking now, so a clip is started once rather than on every tick. */
    private var speaking: NarrationClip? = null

    /** The address handed to [NarrationHost.prepare], so it is handed over once. */
    private var prepared: String = ""

    /**
     * Whether these lines came from a broadcast.
     *
     * Read from the clips themselves rather than passed in: the player already
     * knows what it is playing, and a second flag saying the same thing is a
     * second thing that can disagree. A list is all of one kind, so the first
     * clip settles it.
     */
    private val live: Boolean get() = clips.firstOrNull()?.isLive == true

    /** The moment of the last broadcast line begun, so the next one follows it. */
    private var lastLiveStart = 0L

    /**
     * Milliseconds left of the line now speaking, counted down by the tick.
     *
     * A count rather than a clock: the tick is the only time this class has,
     * and a wall clock here would be a second idea of "now" that a test would
     * have to fake separately.
     */
    private var speakingLeft = 0L

    /** The video's own level, remembered so the ducking can be undone exactly. */
    private var master: Float = 1f

    /** The viewer's two levels. Defaults until the stored ones are read. */
    private var voiceLevel: Float = DEFAULT_VOICE_LEVEL
    private var duckLevel: Float = DEFAULT_DUCK_LEVEL

    /**
     * Set how loud the voice is and how far the video ducks under it.
     *
     * Applied to the line already speaking rather than only to the next one.
     * A volume control that takes effect at the *next* sentence is a control
     * somebody drags, hears nothing, and drags further — and then the next line
     * arrives at the level they overshot to.
     */
    fun setLevels(voice: Float, duck: Float) {
        voiceLevel = voice
        duckLevel = duck
        if (speaking == null) return
        val levels = levels()
        host.setVideoVolume(levels.video)
        host.setSpeechVolume(levels.narration)
    }

    private fun levels() = levelsFor(
        master = master,
        muted = false,
        narrating = true,
        narrationLevel = voiceLevel,
        duckLevel = duckLevel,
    )

    /**
     * Replace the list.
     *
     * An empty list stops narration, deliberately the same call: "narrate
     * nothing" and "stop narrating" are one state, and two methods for it would
     * be two states that can disagree. The server's pass grows while it runs, so
     * this arrives repeatedly with a longer list; anything already speaking
     * keeps speaking.
     */
    fun setClips(clips: List<NarrationClip>) {
        this.clips = clips
        if (clips.isEmpty()) {
            stop()
            return
        }
        if (ticker == null) start()
    }

    fun release() = stop()

    private fun start() {
        master = host.videoVolume()
        ticker = scope.launch {
            while (true) {
                tick()
                delay(TICK_MILLIS)
            }
        }
    }

    private fun stop() {
        ticker?.cancel()
        ticker = null
        host.silence()
        speaking = null
        prepared = ""
        lastLiveStart = 0
        speakingLeft = 0
        // Back to where it was, not up to 1.0: raising it would undo the
        // viewer's own volume setting on the way out of narration.
        host.setVideoVolume(master)
    }

    private fun tick() {
        // Silence while the video is paused. Without this a clip started just
        // before a pause carries on talking over a still frame.
        if (!host.videoIsPlaying) {
            if (speaking != null) hush()
            speakingLeft = 0
            return
        }
        if (live) tickLive() else tickRecorded()
    }

    private fun tickRecorded() {
        val due = clipAt(clips, host.videoPositionSeconds)
        if (due == null) {
            if (speaking != null) hush()
            return
        }
        if (due == speaking) {
            readyNext()
            return
        }
        start(due)
        readyNext()
    }

    /**
     * A broadcast's lines are spoken in turn, as they arrive.
     *
     * **Not matched to the playhead**, and that reverses how this was first
     * written. A line is placed on the wall clock correctly and is ready about
     * thirty seconds after it was said — the clause has to end, the clause
     * after it has to begin so there is a slot, then it is translated and
     * spoken. A viewer of a broadcast sits at the live edge, which is *now*, so
     * a window that closed twenty seconds ago never contains the playhead and
     * nothing would ever play. Measured: the first two lines were 33 and 44
     * seconds behind.
     *
     * So this is simultaneous interpreting, which is what narrating live speech
     * has always been: the voice runs behind the picture and does not catch up.
     * `startsAtEpochMillis` still decides the *order* and what is new; it no
     * longer decides the moment.
     */
    private fun tickLive() {
        if (speakingLeft > 0) {
            speakingLeft -= TICK_MILLIS
            if (speakingLeft > 0) return
        }

        val next = nextLive()
        if (next == null) {
            if (speaking != null) hush()
            return
        }
        lastLiveStart = next.startsAtEpochMillis
        speakingLeft = (next.durationSeconds * 1000).toLong()
        start(next)

        // One ahead, as the recorded path does, so the fetch is not paid for at
        // the moment the line is due.
        val after = nextLive()
        if (after != null && after.clipUrl != prepared) {
            prepared = after.clipUrl
            host.prepare(after.clipUrl)
        }
    }

    /**
     * The next broadcast line to say, skipping anything the queue has fallen
     * too far behind to be worth saying.
     *
     * The bound is against the *newest* line rather than a clock, for the same
     * reason [speakingLeft] is a count: this class has no clock. A phone that
     * was in a pocket for two minutes comes back to a pile, and reading it out
     * in order would put the voice further behind with every line.
     */
    private fun nextLive(): NarrationClip? {
        val newest = clips.lastOrNull()?.startsAtEpochMillis ?: return null
        return clips.firstOrNull {
            it.startsAtEpochMillis > lastLiveStart &&
                newest - it.startsAtEpochMillis <= LIVE_BACKLOG_MILLIS
        }
    }

    /** Duck the video and begin a line. */
    private fun start(clip: NarrationClip) {
        val levels = levels()
        host.setVideoVolume(levels.video)
        speaking = clip
        host.speak(clip.clipUrl, levels.narration)
    }

    /**
     * Buffer the line after the one on screen.
     *
     * One ahead, not several: the clips are seconds apart and a platform
     * holding a queue of them is a platform deciding when they play, which is
     * the one thing this class exists to keep in one place.
     */
    private fun readyNext() {
        val next = nextClipAfter(clips, host.videoPositionSeconds) ?: return
        if (next.clipUrl == prepared) return
        prepared = next.clipUrl
        host.prepare(next.clipUrl)
    }

    private fun hush() {
        speaking = null
        host.silence()
        // The viewer's level, not 1.0 — and not `duckLevel`, which is where the
        // video sits *while* a line is running.
        host.setVideoVolume(master)
    }

    private companion object {
        /**
         * How often the playhead is compared against the clips.
         *
         * A line may start up to one tick late and is replaced by the next one
         * exactly on time, so the tick is subtracted from every clip's tail —
         * and the server leaves no slack to absorb it, having stretched the
         * audio to fill the gap before the next line. At 250ms that was audible
         * as clipped endings; this is the readout's rate divided by two and a
         * half, and it is four comparisons against a list, not four requests.
         */
        const val TICK_MILLIS = 100L

        /**
         * How far behind the newest line a broadcast's queue may run.
         *
         * Half a minute is about the delay the pass has anyway; twice that and
         * the voice is describing a different story. Skipping to the newest
         * costs the lines in between, which is the same judgement the server
         * makes when it drops one that arrived too late.
         */
        const val LIVE_BACKLOG_MILLIS = 30_000L
    }
}
