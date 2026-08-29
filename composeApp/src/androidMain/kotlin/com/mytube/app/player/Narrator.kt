package com.mytube.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.model.clipAt
import com.mytube.app.domain.model.levelsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The second voice.
 *
 * A separate `ExoPlayer` playing one short WAV at a time, driven by where the
 * video has got to. It is not part of the video's playlist: a clip has to start
 * at a moment inside the video, and a playlist can only play things one after
 * another.
 *
 * ## Why the ducking is here
 *
 * Ducking is a fact about two sounds, and something has to hold both. The levels
 * themselves come from `levelsFor` in `domain`, copied from the web app together
 * with the reason they are independent — chained, the voice took its gain from
 * the already-ducked video, so moving one dragged the other.
 *
 * ## Why it polls rather than schedules
 *
 * A timer set for each clip has to be cancelled and rebuilt on every seek, every
 * pause and every list update, and the server's list grows while it plays. Four
 * checks a second against the playhead is right by construction: it cannot drift,
 * a seek needs no special case, and a clip appended a moment ago is picked up on
 * the next tick.
 */
@UnstableApi
class Narrator(context: Context, private val video: Player) {

    private val speaker = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var ticker: Job? = null

    private var clips: List<NarrationClip> = emptyList()

    /** What is speaking now, so a clip is started once rather than every tick. */
    private var speaking: NarrationClip? = null

    /** The video's own level, remembered so ducking can be undone exactly. */
    private var master: Float = 1f

    fun setClips(clips: List<NarrationClip>) {
        this.clips = clips
        if (clips.isEmpty()) {
            stop()
            return
        }
        if (ticker == null) start()
    }

    fun release() {
        stop()
        speaker.release()
    }

    private fun start() {
        master = video.volume
        ticker = scope.launch {
            while (true) {
                tick()
                // Four times a second, the same rate the position readout uses.
                // A clip starting a quarter-second late is inaudible as an
                // error; a timer per clip is a rebuild on every seek.
                delay(250)
            }
        }
    }

    private fun stop() {
        ticker?.cancel()
        ticker = null
        speaker.stop()
        speaker.clearMediaItems()
        speaking = null
        // Put the video back exactly where it was. Ramping it up to 1.0 would
        // undo a viewer's own volume setting on the way out of narration.
        video.volume = master
    }

    private fun tick() {
        // Silence while the video is paused. Without this a clip started just
        // before a pause carries on talking over a still frame.
        if (!video.isPlaying) {
            if (speaking != null) hush()
            return
        }

        val at = video.currentPosition / 1000.0
        val due = clipAt(clips, at)

        if (due == null) {
            if (speaking != null) hush()
            return
        }
        if (due == speaking) return

        val levels = levelsFor(
            master = master,
            muted = false,
            narrating = true,
        )
        video.volume = levels.video
        speaking = due

        speaker.setMediaItem(MediaItem.fromUri(due.clipUrl))
        speaker.volume = levels.narration
        speaker.prepare()
        speaker.play()
    }

    private fun hush() {
        speaking = null
        speaker.stop()
        // Back to the viewer's level, not to 1.0 — and not through duckLevel,
        // which is where the video sits *while* a line runs.
        video.volume = master
    }
}
