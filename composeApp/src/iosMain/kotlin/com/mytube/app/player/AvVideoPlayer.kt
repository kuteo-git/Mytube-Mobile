package com.mytube.app.player

import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.mytube.app.domain.player.Narrator
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.status
import platform.AVFoundation.error
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

    /** Built on first use: most videos are never narrated. */
    private var narrator: Narrator? = null

    /**
     * The lock screen's entry.
     *
     * Built here rather than by the ViewModel: the commands it registers are
     * process-wide, and something that outlives one screen must not be owned by
     * one screen.
     */
    private val nowPlaying = NowPlaying(
        onPlay = { play() },
        onPause = { pause() },
        onSeek = { seekTo(it) },
    )

    init {
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        AVAudioSession.sharedInstance().setActive(true, null)
        startObserving()
    }

    override fun load(media: PlayingMedia, startAtSeconds: Double) {
        _state.update { PlaybackState() }
        val nsUrl = NSURL.URLWithString(media.url) ?: run {
            _state.update { it.copy(error = "bad url") }
            return
        }
        av.replaceCurrentItemWithPlayerItem(AVPlayerItem(nsUrl))
        // Described with the length it has *now*, which is zero until the item
        // loads. `progress` corrects it on the first tick; describing nothing
        // until then would leave the lock screen blank for the seconds a viewer
        // is most likely to look at it.
        nowPlaying.describe(media, _state.value.durationSeconds)
        if (startAtSeconds > 0) seekTo(startAtSeconds)
    }

    /**
     * The second voice, through a `Narrator` sharing this process's audio session.
     *
     * An empty list switches narration off, which is the same call by design:
     * "narrate nothing" and "stop narrating" are one state, and two methods for
     * it would be two states that can disagree.
     *
     * The narrator is built on first use rather than in `init`. It holds a
     * second `AVPlayer`, and most videos are never narrated — building one for
     * every video would be a decoder per video for a feature nobody asked for.
     */
    override fun narrate(clips: List<NarrationClip>) {
        if (clips.isEmpty()) {
            narrator?.release()
            narrator = null
            return
        }
        val live = narrator ?: Narrator(IosNarrationHost(av)).also { narrator = it }
        live.setClips(clips)
    }

    /**
     * The language wanted, kept for the screen to read.
     *
     * AVPlayer cannot be handed a caption file that is not in the HLS manifest,
     * and this server's manifest carries none — the captions are separate `.vtt`
     * files on disk. So nothing is selected in the player: the watch screen
     * fetches the file, parses it, and draws the words over the picture. The
     * player says so through `rendersSubtitles`.
     */
    override val rendersSubtitles: Boolean = false

    override fun showSubtitles(language: String) = Unit

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
        narrator?.release()
        narrator = null
        nowPlaying.clear()
        av.pause()
        // Replacing the item with nothing is what clears the Now Playing entry
        // and the lock-screen controls. Pausing alone leaves the session showing
        // a video the viewer has closed.
        av.replaceCurrentItemWithPlayerItem(null)
        _state.update { PlaybackState() }
    }

    override fun release() {
        // Deliberately *not* cleared here. Releasing hands back this app's hold
        // while the sound carries on — the whole point of the miniplayer — and a
        // lock screen that empties while the audio plays is the fault Android's
        // side of this already measured, in reverse.
        narrator?.release()
        narrator = null
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
            val item = av.currentItem
            val duration = item?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
            // A failed item is otherwise completely silent: the picture stays
            // black, the clock stays at zero, and nothing anywhere says why.
            // Read from this tick rather than through KVO — the observer is
            // already running, and one source of truth for "how is playback
            // getting on" is better than two that can disagree.
            // A live broadcast declares an indefinite duration, which
            // `CMTimeGetSeconds` reports as NaN — so asking for a real number
            // first is what stops every broadcast being called finished the
            // moment it opens. Half a second of slack because the last sample
            // rarely lands exactly on the declared length.
            val ended = duration > 0 && !duration.isNaN() &&
                CMTimeGetSeconds(time) >= duration - 0.5
            val failure = if (item?.status == AVPlayerItemStatusFailed) {
                item.error?.localizedDescription ?: "playback failed"
            } else {
                null
            }
            _state.update {
                it.copy(
                    hasEnded = ended,
                    error = failure ?: it.error,
                    positionSeconds = CMTimeGetSeconds(time),
                    // NaN is what AVPlayer reports before it knows, and it must
                    // not reach a progress bar: NaN/NaN draws as nothing at all.
                    durationSeconds = if (duration.isNaN()) it.durationSeconds else duration,
                    isPlaying = av.timeControlStatus == AVPlayerTimeControlStatusPlaying,
                )
            }
            // The lock screen is told from the same tick. iOS interpolates
            // between updates from the rate, so four a second is more than it
            // needs — but the *rate* has to be right the instant it changes, or
            // the lock screen's clock counts on through a pause.
            val now = _state.value
            nowPlaying.progress(now.positionSeconds, now.isPlaying)
        }
    }
}

class AvVideoPlayerFactory : VideoPlayerFactory {
    override fun create(): VideoPlayer = AvVideoPlayer()
}
