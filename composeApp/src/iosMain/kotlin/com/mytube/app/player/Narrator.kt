package com.mytube.app.player

import com.mytube.app.domain.player.NarrationHost
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.setVolume
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSURL

/**
 * AVFoundation behind [NarrationHost].
 *
 * Seven calls and no decisions: when a line is due, how far the video ducks and
 * what a pause means are all in the common `Narrator`, so this and its Android
 * counterpart cannot behave differently.
 *
 * **It must not touch `AVAudioSession`.** The session is process-wide and
 * `AvVideoPlayer` has already claimed `Playback`; a second player in the same
 * process shares it, which is exactly what lets both be heard at once. Claiming
 * it again here would interrupt the video to say a line over it.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNarrationHost(private val video: AVPlayer) : NarrationHost {

    private val speaker = AVPlayer()

    override val videoIsPlaying: Boolean
        get() = video.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override val videoPositionSeconds: Double
        get() = CMTimeGetSeconds(video.currentTime())

    override fun videoVolume(): Float = video.volume

    override fun setVideoVolume(level: Float) = video.setVolume(level)

    override fun speak(url: String, volume: Float) {
        // A URL that will not parse is skipped rather than thrown: a clip that
        // cannot be spoken should cost that line, not the video.
        val nsUrl = NSURL.URLWithString(url) ?: return
        speaker.replaceCurrentItemWithPlayerItem(AVPlayerItem(nsUrl))
        speaker.setVolume(volume)
        speaker.play()
    }

    override fun setSpeechVolume(level: Float) = speaker.setVolume(level)

    override fun silence() {
        speaker.pause()
        // Replacing with nothing, not just pausing: a paused player holds the
        // clip and its decoder for as long as the video runs.
        speaker.replaceCurrentItemWithPlayerItem(null)
    }
}
