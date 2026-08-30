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

    private val speaker = ExoPlayer.Builder(context).build()

    override val videoIsPlaying: Boolean get() = video.isPlaying

    override val videoPositionSeconds: Double get() = video.currentPosition / 1000.0

    override fun videoVolume(): Float = video.volume

    override fun setVideoVolume(level: Float) {
        video.volume = level
    }

    override fun speak(url: String, volume: Float) {
        speaker.setMediaItem(MediaItem.fromUri(url))
        speaker.volume = volume
        speaker.prepare()
        speaker.play()
    }

    override fun setSpeechVolume(level: Float) {
        speaker.volume = level
    }

    override fun silence() {
        speaker.stop()
        speaker.clearMediaItems()
    }

    /** Hands back the decoder. Only the owner of this host may call it. */
    fun dispose() = speaker.release()
}
