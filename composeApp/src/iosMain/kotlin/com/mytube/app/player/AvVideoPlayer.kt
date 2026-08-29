package com.mytube.app.player

import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

/**
 * AVPlayer behind the port.
 *
 * ## Why the audio session is configured here
 *
 * `AVAudioSessionCategoryPlayback` is what tells iOS this app's sound is the
 * point rather than an incidental beep. Without it, audio stops the moment the
 * screen locks — which would defeat the one thing this app exists for, since the
 * server charter's risk 4 says a native app is the only path to background
 * playback on iOS.
 *
 * It is set when a player is created rather than at launch: the category is
 * global to the process, and claiming playback while nothing is playing takes
 * audio focus from whatever else the phone is doing.
 *
 * **Background playback also needs `UIBackgroundModes: audio` in the Info.plist,
 * and that is not written yet** — there is no Xcode project to hold one. Until
 * then this is correct and untested on a device.
 *
 * ## Why HLS needs nothing said about it
 *
 * AVPlayer plays HLS natively; it is the format the platform prefers. The ladder
 * the server writes is AVPlayer's to climb, and — measured on the web side of
 * this system — iOS gives a client no way to pin a level, which is exactly why
 * the device ceiling travels as `?max=` on the URL instead.
 */
@OptIn(ExperimentalForeignApi::class)
class AvVideoPlayer : VideoPlayer {

    /**
     * Exposed for the Compose surface to attach an `AVPlayerLayer` to.
     *
     * The same deliberate leak as the Android side: a platform view needs the
     * platform object, and inventing a wrapper to avoid saying so would hide the
     * fact without changing it.
     */
    val av: AVPlayer = AVPlayer()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var observer: Any? = null

    init {
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        AVAudioSession.sharedInstance().setActive(true, null)
        startObserving()
    }

    override fun load(media: PlayingMedia, startAtSeconds: Double) {
        _state.update { PlaybackState() }
        // The lock screen's title and artist come from MPNowPlayingInfoCenter on
        // iOS rather than from the player item, and that is not wired up yet —
        // recorded as missing rather than half-done, since there is no Xcode
        // project to run it in.
        val nsUrl = NSURL.URLWithString(media.url) ?: run {
            _state.update { it.copy(error = "bad url") }
            return
        }
        av.replaceCurrentItemWithPlayerItem(AVPlayerItem(nsUrl))
        if (startAtSeconds > 0) seekTo(startAtSeconds)
    }

    override fun play() = av.play()

    override fun pause() = av.pause()

    override fun seekTo(seconds: Double) {
        // 600 is the timescale Apple's own samples use: it divides evenly by the
        // common frame rates, so a seek lands on a frame boundary rather than
        // between two.
        av.seekToTime(CMTimeMakeWithSeconds(seconds, 600))
        _state.update { it.copy(positionSeconds = seconds) }
    }

    override fun stop() {
        av.pause()
        // Replacing the item with nothing is what clears the Now Playing entry
        // and the lock-screen controls. Pausing alone leaves the session showing
        // a video the viewer has closed.
        av.replaceCurrentItemWithPlayerItem(null)
        _state.update { PlaybackState() }
    }

    override fun release() {
        observer?.let { av.removeTimeObserver(it) }
        observer = null
        av.pause()
        // Handing the session back, so another app's audio can resume. Holding
        // it after playback ends is how an app becomes the reason music stopped.
        AVAudioSession.sharedInstance().setActive(false, null)
    }

    /**
     * A periodic observer rather than a coroutine ticker.
     *
     * AVPlayer offers one, it is driven by the playback clock rather than by
     * wall time, and it stops on its own while paused — three things a `delay`
     * loop would have to be told.
     */
    private fun startObserving() {
        observer = av.addPeriodicTimeObserverForInterval(
            interval = CMTimeMakeWithSeconds(0.25, 600),
            queue = dispatch_get_main_queue(),
        ) { time ->
            val duration = av.currentItem?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
            _state.update {
                it.copy(
                    positionSeconds = CMTimeGetSeconds(time),
                    // NaN is what AVPlayer reports before it knows, and it must
                    // not reach a progress bar: NaN/NaN draws as nothing at all.
                    durationSeconds = if (duration.isNaN()) it.durationSeconds else duration,
                    isPlaying = av.timeControlStatus == AVPlayerTimeControlStatusPlaying,
                )
            }
        }
    }
}

class AvVideoPlayerFactory : VideoPlayerFactory {
    override fun create(): VideoPlayer = AvVideoPlayer()
}
