package com.mytube.app.player

import com.mytube.app.domain.model.DEFAULT_DUCK_LEVEL
import com.mytube.app.domain.model.DEFAULT_VOICE_LEVEL
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.mytube.app.domain.player.Narrator
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVMediaCharacteristicLegible
import platform.AVFoundation.AVMediaTypeSubtitle
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.asset
import platform.AVFoundation.extendedLanguageTag
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
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
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.AVFoundation.CMTimeRangeValue
import platform.AVFoundation.seekableTimeRanges
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSValue
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
     * The viewer's two levels, held here because the narrator is built lazily.
     *
     * Levels set before the first line is spoken would otherwise be lost, and
     * that is the ordinary case: they are read from the device when the video
     * opens, minutes before the server's first clip is ready.
     */
    private var voiceLevel: Float = DEFAULT_VOICE_LEVEL
    private var duckLevel: Float = DEFAULT_DUCK_LEVEL

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
        isLive = media.isLive
        // Kept so a stall can be recovered from without the screen being asked
        // again. A recovery that had to go back through the ViewModel would be
        // a recovery that cannot happen with the app in the background, which is
        // exactly when it is needed. @see recoverIfStalled
        loaded = media
        lastRecovery = 0.0
        movedAt = now()
        movedTo = -1.0
        startWatchdog()
        _state.update { PlaybackState(isLive = media.isLive) }
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
        live.setLevels(voiceLevel, duckLevel)
        live.setClips(clips)
    }

    override fun setNarrationLevels(voice: Float, duck: Float) {
        voiceLevel = voice
        duckLevel = duck
        narrator?.setLevels(voice, duck)
    }

    /**
     * The language wanted, kept for the screen to read.
     *
     * AVPlayer cannot be handed a caption file that is not in the HLS manifest.
     * For a recorded video this server's manifest carries none — the captions
     * are separate `.vtt` files on disk — so the watch screen fetches the file,
     * parses it, and draws the words itself.
     *
     * A broadcast is the other way round: its captions arrive *inside* the
     * manifest, as a playlist the gateway names in the master it writes. There
     * is no file to fetch and nothing for the screen to draw, and AVPlayer
     * renders them once the track is selected.
     *
     * So this is asked of the item rather than declared for the platform: the
     * player renders subtitles exactly when it has a track to render.
     */
    override val rendersSubtitles: Boolean
        get() = legibleGroup() != null

    /**
     * The language the viewer has asked for, applied again once the item is
     * ready.
     *
     * The screen asks for subtitles the moment the media is handed over, which
     * is before the asset knows what tracks it has — and asking then is what
     * broke playback outright, see [legibleGroup].
     */
    private var wantedSubtitles = ""

    /**
     * Whether the item being played is a broadcast, remembered from `load`.
     *
     * The window is only read for one, because `seekableTimeRanges` on a
     * recorded video is the whole file and would say the same thing its
     * duration already does — two answers that can disagree while one loads.
     */
    private var isLive = false

    /**
     * The media on screen, and whether its sound is wanted.
     *
     * Both exist for one reason: a stream that stops has to be started again
     * without asking anybody. `wantsToPlay` is the difference between "the
     * picture is not moving because it broke" and "because somebody pressed
     * pause", and reattaching over the second would be the app restarting a
     * video the viewer had stopped.
     */
    private var loaded: PlayingMedia? = null
    private var wantsToPlay = false

    /** Wall clock, in seconds. The playhead cannot time its own absence. */
    private fun now(): Double = NSDate().timeIntervalSince1970

    /** When the playhead last moved, and where to. */
    private var movedAt = 0.0
    private var movedTo = -1.0

    /** When the last reattach was made, so a dead server is not hammered. */
    private var lastRecovery = 0.0

    /**
     * The watchdog's own clock, and it cannot be the player's.
     *
     * `addPeriodicTimeObserverForInterval` is driven by the **playback clock** —
     * the comment on `startObserving` says so, and says it stops while paused.
     * A stall is a stopped playback clock, so a watchdog hung on that tick is
     * one that stops at exactly the moment it is needed. Measured with a tagged
     * log through a gateway cut mid-stream: two lines, `stalledFor=0.1`, and
     * then silence for the whole outage.
     *
     * So this is an `NSTimer` on the main run loop, which keeps running while
     * the app is backgrounded for audio — the case the whole thing exists for.
     */
    private var watchdog: NSTimer? = null


    private fun startWatchdog() {
        if (watchdog != null) return
        watchdog = NSTimer.scheduledTimerWithTimeInterval(
            interval = WATCHDOG_INTERVAL_SECONDS,
            repeats = true,
        ) {
            recoverIfStalled(_state.value.positionSeconds)
        }
    }

    /**
     * Start the stream again when it has stopped and nobody asked it to.
     *
     * **Measured before it existed**, driving the simulator through a gateway
     * that could be cut and restored on command: a broadcast survives a 10s
     * outage on its own — AVPlayer retries segments — and does **not** survive
     * 45s. After the longer cut the picture never moved again, with the server
     * back and answering. Mean pixel change over six seconds: 18.11 while
     * playing, 0.00 after.
     *
     * That is the whole of the background-playback question. iOS does not kill
     * an app on a timer; it keeps one alive for as long as it is *making sound*
     * and suspends it within seconds of the sound stopping. So a stall is not a
     * cosmetic fault on a broadcast left playing overnight — it is the app
     * ending.
     *
     * A reattach rather than a seek: the item is what failed, and AVPlayer will
     * not re-fetch a playlist it has given up on. A broadcast needs no position
     * — a fresh live item starts at the edge, which is where somebody who has
     * been listening wants to be, not forty seconds back at the moment of the
     * outage. A recording is put back where the playhead was.
     *
     * There is no attempt limit. The wifi coming back an hour later is the case
     * this is for, and an app that stopped trying after five goes would be one
     * that has to be reopened by hand — which is the thing being fixed.
     */
    private fun recoverIfStalled(position: Double) {
        if (!wantsToPlay) return
        val item = av.currentItem
        val media = loaded ?: return

        val failed = item == null || item.status == AVPlayerItemStatusFailed
        if (!failed && position != movedTo) {
            movedTo = position
            movedAt = now()
                return
        }
        val stalledFor = now() - movedAt
        if (!failed && stalledFor < STALL_SECONDS) return
        // Spaced out rather than tried on every tick. A tick is a quarter of a
        // second, and a server that is down answers a reattach as fast as it
        // answers anything — four requests a second at a router that is off.
        if (now() - lastRecovery < RECOVERY_INTERVAL_SECONDS) return
        lastRecovery = now()

        val url = NSURL.URLWithString(media.url) ?: return
        av.replaceCurrentItemWithPlayerItem(AVPlayerItem(url))
        if (!isLive && position > 0) seekTo(position)
        // The track has to be chosen again: it belonged to the item that died.
        // `applySubtitles` is a no-op until the new item is ready and the tick
        // below calls it again, which is the path it was already built for.
        if (wantedSubtitles.isNotEmpty()) applySubtitles()
        av.play()
        movedAt = now()
    }

    /**
     * The item's caption tracks, or null when it has none or cannot say yet.
     *
     * **The readiness check is load-bearing, not a tidy-up.**
     * `mediaSelectionGroupForMediaCharacteristic` is a *synchronous* accessor:
     * on an asset that has not loaded that property it blocks the calling
     * thread until it can answer, and for a live HLS asset that is a long
     * time — on the main thread it is the whole app. Measured on a real phone:
     * a broadcast that had played a minute earlier would not start at all,
     * while the same stream played in the browser, because the browser has no
     * such call.
     *
     * `AVMediaCharacteristicLegible` is the characteristic HLS subtitle
     * renditions are grouped under. A recorded video from this server has no
     * such group at all, which is what makes [rendersSubtitles] answer
     * correctly without being told what kind of video is playing.
     */
    private fun legibleGroup(): AVMediaSelectionGroup? {
        val item = av.currentItem ?: return null
        if (item.status != AVPlayerItemStatusReadyToPlay) return null
        val group = item.asset
            .mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
            ?: return null
        // Only real subtitle renditions count, and the filter is the whole of a
        // bug rather than tidiness.
        //
        // A master playlist that does not say `CLOSED-CAPTIONS=NONE` leaves
        // AVFoundation free to assume CEA-608 captions might be buried in the
        // video, and it duly reports a legible group holding one option:
        // `name=CC, type=clcp, tag=nil`. This server's recorded masters say no
        // such thing, so **every** recorded video looked like a video whose
        // captions the player draws — and the screen, believing that, never
        // fetched the `.vtt` beside the file. Measured with the same probe
        // against both masters: a broadcast answers `type=sbtl, tag=en`, a
        // recording answers `type=clcp, tag=nil`.
        //
        // It hid behind the readiness check above. `legibleGroup` answers null
        // until the item is ready, and on first open the CC button is pressed
        // before that — so the fetch happened and the words appeared. Send the
        // app to the background and bring it back, by which time the asset has
        // certainly loaded, and the same press drew nothing. That is exactly
        // the sequence this was reported as.
        val real = group.options
            .filterIsInstance<AVMediaSelectionOption>()
            .any { it.mediaType == AVMediaTypeSubtitle }
        return if (real) group else null
    }

    override fun showSubtitles(language: String) {
        wantedSubtitles = language
        applySubtitles()
    }

    private fun applySubtitles() {
        val language = wantedSubtitles
        val item = av.currentItem ?: return
        val group = legibleGroup() ?: return

        if (language.isEmpty()) {
            // Off is a selection of its own, not the absence of one: leaving the
            // previous option in place would show captions nobody asked for.
            item.selectMediaOption(null, group)
            return
        }

        val option = group.options
            .filterIsInstance<AVMediaSelectionOption>()
            .firstOrNull { it.extendedLanguageTag == language }
            ?: group.options
                .filterIsInstance<AVMediaSelectionOption>()
                .firstOrNull { it.extendedLanguageTag?.startsWith("$language-") == true }
            ?: return
        item.selectMediaOption(option, group)
    }

    override fun play() {
        wantsToPlay = true
        movedAt = now()
        av.play()
    }

    override fun pause() {
        wantsToPlay = false
        av.pause()
    }

    override fun seekTo(seconds: Double) {
        // 600 is the timescale Apple's own samples use: it divides evenly by the
        // common frame rates, so a seek lands on a frame boundary rather than
        // between two.
        av.seekToTime(CMTimeMakeWithSeconds(seconds, 600))
        _state.update { it.copy(positionSeconds = seconds) }
    }

    override fun stop() {
        wantsToPlay = false
        loaded = null
        watchdog?.invalidate()
        watchdog = null
        narrator?.release()
        narrator = null
        nowPlaying.resign()
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
        // The lock-screen *entry* is deliberately left standing (see above);
        // its *buttons* are not. `MPRemoteCommandCenter` is process-wide, so a
        // released player that still answers Play is a second video heard over
        // the one the viewer opened — measured on the phone.
        nowPlaying.resign()
        watchdog?.invalidate()
        watchdog = null
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
            // The rewindable window, which is the only length a broadcast
            // declares. Read every tick rather than once: it slides forward
            // with the picture, and a window read at open is wrong a minute
            // later. Several ranges is legal after a seek across a gap, so the
            // first start and the last end are what bound the whole of it.
            var windowStart = 0.0
            var windowEnd = 0.0
            if (isLive) {
                val ranges = item?.seekableTimeRanges.orEmpty()
                if (ranges.isNotEmpty()) {
                    (ranges.first() as? NSValue)?.CMTimeRangeValue?.useContents {
                        windowStart = CMTimeGetSeconds(start.readValue())
                    }
                    (ranges.last() as? NSValue)?.CMTimeRangeValue?.let {
                        windowEnd = CMTimeGetSeconds(CMTimeRangeGetEnd(it))
                    }
                }
                if (windowStart.isNaN() || windowEnd.isNaN()) {
                    windowStart = 0.0
                    windowEnd = 0.0
                }
            }
            _state.update {
                it.copy(
                    hasEnded = ended,
                    liveStartSeconds = windowStart,
                    liveEndSeconds = windowEnd,
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
            nowPlaying.progress(now.positionSeconds, now.isPlaying, now.durationSeconds)
            // The track the viewer asked for, applied once the item can say
            // what tracks it has. Asking at load is what this observer exists
            // to replace — see `legibleGroup`.
            if (wantedSubtitles.isNotEmpty()) applySubtitles()
        }
    }
}

/**
 * How long a playhead may stand still before the stream is started again.
 *
 * Six seconds, and it is a compromise between two measured facts: AVPlayer's
 * own retry rides out a ten-second outage without help, so anything shorter
 * fights a recovery that was already working, and a broadcast that has been
 * still for six seconds is not buffering — a live playlist declares five-second
 * segments.
 */
private const val STALL_SECONDS = 6.0

/** How often a reattach may be attempted. @see AvVideoPlayer.recoverIfStalled */
private const val RECOVERY_INTERVAL_SECONDS = 5.0

/**
 * How often the watchdog looks.
 *
 * Two seconds against the player's own quarter-second tick: this one runs on
 * wall time and has to keep running with the screen off, so it is as slow as it
 * can be and still notice a six-second stall promptly.
 */
private const val WATCHDOG_INTERVAL_SECONDS = 2.0


class AvVideoPlayerFactory : VideoPlayerFactory {
    override fun create(): VideoPlayer = AvVideoPlayer()
}
