package com.mytube.app.player

import com.mytube.app.domain.player.NarrationHost
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.currentDate
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.setVolume
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.volume
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970

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

    /**
     * Two speakers, used in turn.
     *
     * `replaceCurrentItemWithPlayerItem` is what stops the line already running,
     * so one player cannot buffer the next while playing this one. The clip after
     * this is prepared on the idle player, and starting it is a swap rather than
     * a fetch — which is what keeps a line's tail, the server having stretched
     * its audio to fill the gap before the next line with nothing to spare.
     */
    private val speakers = listOf(AVPlayer(), AVPlayer())

    /** Which of the two is the one that may be heard. */
    private var current = 0

    /** What each player holds, so an address already loaded is not loaded again. */
    private val loaded = arrayOf("", "")

    private val idle get() = 1 - current

    /**
     * Attach an item, which is what makes AVFoundation start filling its buffer.
     *
     * An `AVPlayerItem` on its own loads nothing; only a player asks for the
     * bytes. False when the address will not parse — a clip that cannot be
     * spoken costs that line, not the video.
     */
    private fun load(slot: Int, url: String): Boolean {
        if (loaded[slot] == url) return true
        val nsUrl = NSURL.URLWithString(url) ?: return false
        speakers[slot].replaceCurrentItemWithPlayerItem(AVPlayerItem(nsUrl))
        loaded[slot] = url
        return true
    }

    override val videoIsPlaying: Boolean
        get() = video.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override val videoPositionSeconds: Double
        get() = CMTimeGetSeconds(video.currentTime())

    /**
     * The playhead on the wall clock, for a live stream that carries one.
     *
     * `currentDate()` is AVFoundation's reading of `EXT-X-PROGRAM-DATE-TIME`,
     * and it is null for anything without one — every recorded video — which
     * is reported here as the `0` the port defines as "no clock".
     */
    override val videoEpochMillis: Long
        get() {
            val date = video.currentItem?.currentDate() ?: return 0
            return (date.timeIntervalSince1970 * 1000).toLong()
        }

    override fun videoVolume(): Float = video.volume

    override fun setVideoVolume(level: Float) = video.setVolume(level)

    override fun prepare(url: String) {
        // Silent while it buffers: the item is attached to a real player, and a
        // swap arriving mid-tick must not be audible before `speak` has set the
        // viewer's level.
        speakers[idle].setVolume(0f)
        load(idle, url)
    }

    override fun speak(url: String, volume: Float) {
        // The prepared player if it is the one holding this line, otherwise the
        // current one — a seek reaches a clip nothing was buffering.
        if (loaded[idle] == url) {
            speakers[current].pause()
            current = idle
        }
        if (!load(current, url)) return
        speakers[current].seekToTime(CMTimeMake(0, 1))
        speakers[current].setVolume(volume)
        speakers[current].play()
    }

    override fun setSpeechVolume(level: Float) = speakers[current].setVolume(level)

    override fun silence() {
        speakers.forEachIndexed { slot, player ->
            player.pause()
            // Replacing with nothing, not just pausing: a paused player holds the
            // clip and its decoder for as long as the video runs.
            player.replaceCurrentItemWithPlayerItem(null)
            loaded[slot] = ""
        }
    }
}
