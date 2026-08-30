package com.mytube.app.domain.player

import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.clipAt
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

    /** Begin one clip. Anything already speaking is replaced. */
    fun speak(url: String, volume: Float)

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

    /** The video's own level, remembered so the ducking can be undone exactly. */
    private var master: Float = 1f

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
        // Back to where it was, not up to 1.0: raising it would undo the
        // viewer's own volume setting on the way out of narration.
        host.setVideoVolume(master)
    }

    private fun tick() {
        // Silence while the video is paused. Without this a clip started just
        // before a pause carries on talking over a still frame.
        if (!host.videoIsPlaying) {
            if (speaking != null) hush()
            return
        }

        val due = clipAt(clips, host.videoPositionSeconds)
        if (due == null) {
            if (speaking != null) hush()
            return
        }
        if (due == speaking) return

        val levels = levelsFor(master = master, muted = false, narrating = true)
        host.setVideoVolume(levels.video)
        speaking = due
        host.speak(due.clipUrl, levels.narration)
    }

    private fun hush() {
        speaking = null
        host.silence()
        // The viewer's level, not 1.0 — and not `duckLevel`, which is where the
        // video sits *while* a line is running.
        host.setVideoVolume(master)
    }

    private companion object {
        /** The same rate the position readout uses. */
        const val TICK_MILLIS = 250L
    }
}
