package com.mytube.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mytube.app.domain.player.NarrationHost

/**
 * Media3 behind [NarrationHost].
 *
 * Seven calls and no decisions. This file used to hold the ticker, the ducking and
 * the "what is speaking now" bookkeeping as well, and so did its iOS
 * counterpart — two copies of one behaviour, which is two places to fix
 * everything and two ways for the platforms to drift. All of it moved to the
 * common `Narrator`; what is left is the part that genuinely cannot be shared,
 * because ExoPlayer and AVPlayer have no type in common.
 *
 * The speaker is a second `ExoPlayer` rather than another item in the video's
 * playlist: a clip has to begin at a moment *inside* the video, and a playlist
 * can only play things one after another.
 */
@UnstableApi
class AndroidNarrationHost(context: Context, private val video: Player) : NarrationHost {

    /**
     * Two speakers, used in turn.
     *
     * One player cannot buffer the next line while playing this one — loading a
     * new item is what stops the current one. So the clip after this is prepared
     * on the idle player, and starting it is a swap rather than a fetch.
     */
    private val speakers = listOf(
        ExoPlayer.Builder(context).build(),
        ExoPlayer.Builder(context).build(),
    )

    /** Which of the two is the one that may be heard. */
    private var current = 0

    /** What each player holds, so an address already loaded is not loaded again. */
    private val loaded = arrayOf("", "")

    private val idle get() = 1 - current

    private fun load(slot: Int, url: String) {
        if (loaded[slot] == url) return
        speakers[slot].setMediaItem(MediaItem.fromUri(url))
        speakers[slot].prepare()
        loaded[slot] = url
    }

    override val videoIsPlaying: Boolean get() = video.isPlaying

    override val videoPositionSeconds: Double get() = video.currentPosition / 1000.0

    override fun videoVolume(): Float = video.volume

    override fun setVideoVolume(level: Float) {
        video.volume = level
    }

    override fun prepare(url: String) {
        // Onto the idle player, and at zero volume: `prepare` only buffers, but
        // a swap that arrives mid-tick must not be audible before `speak` has
        // set the viewer's level.
        speakers[idle].volume = 0f
        load(idle, url)
    }

    override fun speak(url: String, volume: Float) {
        // The prepared player if it is the one holding this line, otherwise the
        // current one — a seek reaches a clip nothing was buffering.
        if (loaded[idle] == url) {
            speakers[current].stop()
            current = idle
        }
        load(current, url)
        speakers[current].volume = volume
        speakers[current].seekTo(0)
        speakers[current].play()
    }

    override fun setSpeechVolume(level: Float) {
        speakers[current].volume = level
    }

    override fun silence() {
        speakers.forEach {
            it.stop()
            it.clearMediaItems()
        }
        loaded[0] = ""
        loaded[1] = ""
    }

    /** Hands back the decoder. Only the owner of this host may call it. */
    fun dispose() = speakers.forEach { it.release() }
}
