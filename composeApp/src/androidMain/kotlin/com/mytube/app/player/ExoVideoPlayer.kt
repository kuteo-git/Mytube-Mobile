package com.mytube.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.mytube.app.domain.model.DEFAULT_DUCK_LEVEL
import com.mytube.app.domain.model.DEFAULT_VOICE_LEVEL
import com.mytube.app.domain.model.NarrationClip
import com.mytube.app.domain.player.Narrator
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The app's handle on the player that lives in [PlaybackService].
 *
 * ## Why this is a MediaController and not an ExoPlayer
 *
 * It was an ExoPlayer, held by the ViewModel, and that cannot survive the screen
 * going off — which is the one thing this app was written to do. The ownership
 * had to invert: the service owns the player, and this connects to it.
 *
 * `MediaController` *is* a `Player`, so `PlayerView` takes it directly and the
 * surface never learns the difference. What changes is that the thing actually
 * decoding lives in a foreground service the system will leave alone.
 *
 * ## Why a load can arrive before the connection
 *
 * The controller comes back through a future: the service has to be started and
 * bound first. A viewer who has pressed play does not wait for architecture, so
 * a `load()` that arrives early is remembered and replayed the moment the
 * connection lands. Without that, opening a video from a cold start is a black
 * screen — the controller connects a few hundred milliseconds later with nothing
 * to play, and nothing asks again.
 *
 * HLS needs no configuration: `media3-exoplayer-hls` is on the classpath and the
 * source type comes from the URL. The ladder the server writes is then
 * ExoPlayer's to climb, which is the whole reason the server publishes a real
 * ladder rather than one rendition.
 */
@UnstableApi
/**
 * Media3's band for "the source could not be read".
 *
 * A range rather than a list of constants: the codes in it are documented as
 * 2000..2999 and the set grows between versions, so naming five of them is a
 * check that goes quietly out of date. @see PlaybackException
 */
private val IO_ERRORS =
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED..(PlaybackException.ERROR_CODE_IO_UNSPECIFIED + 999)

class ExoVideoPlayer(private val context: Context) : VideoPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var ticker: Job? = null

    /**
     * The connected controller, or null while connecting.
     *
     * Read by the Compose surface, which draws nothing until it exists.
     */
    var controller: MediaController? = null
        private set

    /** A load that arrived before the connection did. */
    private var pending: Pair<PlayingMedia, Double>? = null

    /**
     * The second voice, built once the controller exists.
     *
     * It needs the video's player to read the playhead from and to duck, and
     * that is the controller — so it cannot exist before the connection does.
     */
    private var narrator: Narrator? = null

    /** The Media3 half, kept so its decoder can be handed back. */
    private var narrationHost: AndroidNarrationHost? = null

    /** Clips that arrived before the narrator did, replayed on connection. */
    private var pendingClips: List<NarrationClip> = emptyList()

    /**
     * The viewer's two levels, held here rather than in the narrator.
     *
     * The narrator does not exist until the service connection lands, and the
     * levels are read from the device the moment a video opens — so without a
     * home outside it, every launch would play its first video at the defaults
     * whatever the sliders said.
     */
    private var voiceLevel: Float = DEFAULT_VOICE_LEVEL
    private var duckLevel: Float = DEFAULT_DUCK_LEVEL

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            // The position only moves while something is playing, so the ticker
            // runs only then. A timer firing four times a second against a
            // paused video is battery spent on a number that is not changing.
            if (isPlaying) startTicking() else stopTicking()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationSeconds = durationOrZero(),
                    // Media3 says so outright, so nothing here has to compare a
                    // position with a duration and guess.
                    hasEnded = playbackState == Player.STATE_ENDED,
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // errorCodeName rather than the message: the message is often null,
            // and a blank error line reads as the app having lost interest.
            _state.update { it.copy(error = error.errorCodeName, isBuffering = false) }

            // A network error is not the end of the video, and Media3 stops
            // anyway: after an error the player is idle and stays idle until
            // somebody prepares it again. On a broadcast left playing with the
            // screen off, nobody is there to. iOS measures what that costs — a
            // 45s outage killed playback for good, and iOS suspends an app
            // within seconds of its sound stopping — so the same recovery
            // belongs on both sides.
            //
            // `isRecoverable` is Media3's own judgement: a source that timed
            // out is worth another go, a video this device cannot decode is
            // not. Retrying the second forever would be a loop nothing breaks.
            val code = error.errorCode
            val controller = controller ?: return
            when {
                // The live case with a name of its own: the player fell so far
                // behind that the position it holds is no longer inside the
                // window the playlist still lists. Preparing alone would fail
                // again at the same place; the default position *is* the live
                // edge, which is where somebody who has been listening wants to
                // be rather than back at the moment the wifi went.
                code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    controller.seekToDefaultPosition()
                    controller.prepare()
                    controller.play()
                }

                // Everything Media3 numbers in the IO band — 2000..2999, which
                // is the documented range for a source that could not be read:
                // a connection refused, a timeout, an HTTP status. Worth
                // another go, unlike a decoder that cannot play this file at
                // all, which would be a loop nothing breaks.
                code in IO_ERRORS || code == PlaybackException.ERROR_CODE_TIMEOUT -> {
                    controller.prepare()
                    controller.play()
                }
            }
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val connected = future.get().also { it.addListener(listener) }
                controller = connected
                val host = AndroidNarrationHost(context, connected)
                narrationHost = host
                narrator = Narrator(host).also {
                    it.setLevels(voiceLevel, duckLevel)
                    if (pendingClips.isNotEmpty()) it.setClips(pendingClips)
                }
                pendingClips = emptyList()
                pending?.let { (media, at) -> start(media, at) }
                pending = null
            },
            // Runs on the thread that completes the future, which Media3
            // guarantees is the one the controller was built on — the main
            // thread. A MediaController may only be touched from there.
            MoreExecutors.directExecutor(),
        )
    }

    override fun load(media: PlayingMedia, startAtSeconds: Double) {
        _state.update { PlaybackState(isLive = media.isLive) }
        if (controller == null) {
            pending = media to startAtSeconds
            return
        }
        start(media, startAtSeconds)
    }

    override fun narrate(clips: List<NarrationClip>) {
        val live = narrator
        if (live == null) {
            // Remembered for the same reason a load is: the connection lands a
            // few hundred milliseconds later, and a viewer who switched
            // narration on before then must not have to switch it on again.
            pendingClips = clips
            return
        }
        live.setClips(clips)
        live.setLevels(voiceLevel, duckLevel)
    }

    override fun setNarrationLevels(voice: Float, duck: Float) {
        voiceLevel = voice
        duckLevel = duck
        narrator?.setLevels(voice, duck)
    }

    override fun play() {
        controller?.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun seekTo(seconds: Double) {
        controller?.seekTo((seconds * 1000).toLong())
        // Written straight away rather than waited for. A seek bar that snaps
        // back to the old position for a frame before jumping is the thing that
        // makes dragging one feel broken.
        _state.update { it.copy(positionSeconds = seconds) }
    }

    override fun stop() {
        narrator?.release()
        narrator = null
        narrationHost?.dispose()
        narrationHost = null
        // Both, in this order. `stop()` alone leaves the item loaded, so Media3
        // keeps the session — and its notification — alive over a player with
        // nothing to play; clearing the queue is what tells the service the
        // session is over.
        controller?.stop()
        controller?.clearMediaItems()
        // A load that never got a connection must not arrive after this and
        // start the video again.
        pending = null
        _state.update { PlaybackState() }
    }

    /**
     * Lets go of the connection, and deliberately does **not** stop the player.
     *
     * This is the difference between a screen closing and playback ending. The
     * service keeps the sound going behind its notification; leaving the watch
     * screen is not a request to stop listening. The ways to stop are the
     * notification and swiping the app out of recents.
     */
    override fun release() {
        stopTicking()
        narrator?.release()
        narrator = null
        narrationHost?.dispose()
        narrationHost = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /** ExoPlayer is given the .vtt files at load and draws the cues itself. */
    override val rendersSubtitles: Boolean = true

    override fun showSubtitles(language: String) {
        val player = controller ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            // Both together. Setting only the language leaves the type disabled
            // if it was switched off, and disabling only the type leaves a
            // language selected that nothing will show — the two have to move as
            // one or the menu and the picture disagree.
            .setPreferredTextLanguage(language.ifEmpty { null })
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, language.isEmpty())
            .build()
    }

    private fun start(media: PlayingMedia, startAtSeconds: Double) {
        val player = controller ?: return
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(media.url)
                // Side-loaded, not part of the HLS manifest: the captions live
                // beside the video on this server as .vtt files, and the ladder
                // the server writes carries no text tracks at all.
                .setSubtitleConfigurations(
                    media.subtitles.map {
                        MediaItem.SubtitleConfiguration.Builder(it.url.toUri())
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(it.language)
                            .setLabel(it.label)
                            // Not SELECTION_FLAG_DEFAULT. A track marked default
                            // is shown the moment the video opens, and subtitles
                            // nobody asked for over a video they did not choose
                            // them for is the wrong way round.
                            .build()
                    },
                )
                // What the notification and the lock screen draw. Without it
                // Media3 falls back to the app label, and the notification reads
                // "Mytube is running" — which is exactly as useful as silence to
                // somebody reaching into a pocket.
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(media.title)
                        .setArtist(media.channel)
                        .setArtworkUri(media.artworkUrl.takeIf { it.isNotEmpty() }?.toUri())
                        .build(),
                )
                .build(),
            // The start position goes *into* setMediaItem, not into a seekTo
            // after it.
            //
            // Measured: a seek issued between setMediaItem and prepare is
            // dropped. Before prepare the controller's timeline is empty, so
            // there is no window to seek within, and the video opened at zero —
            // a video left at 18 seconds of 74 restarted every time, silently,
            // while the server held the right number all along.
            (startAtSeconds * 1000).toLong().coerceAtLeast(0),
        )
        player.prepare()
        player.play()
    }

    private fun durationOrZero(): Double =
        controller?.duration?.takeIf { it > 0 }?.div(1000.0) ?: 0.0

    /**
     * The rewindable window of a broadcast, or `0.0 to 0.0` when there is none.
     *
     * Media3 counts a position from the start of the current window, so this
     * one begins at zero — the counterpart of iOS's `seekableTimeRanges`, whose
     * numbers are the item's own and can begin anywhere. The two platforms
     * therefore report different starts for the same broadcast, and that is
     * correct: each is the timebase its own `seekTo` takes.
     *
     * `Window.isLive()` rather than the caller's flag: this is the timeline's
     * own answer, and it is what decides whether `durationMs` is a window or a
     * length. A window whose duration is unset is a stream with no rewind at
     * all, which is `0.0 to 0.0` and draws no bar.
     */
    private fun liveWindowSeconds(): Pair<Double, Double> {
        val player = controller ?: return 0.0 to 0.0
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return 0.0 to 0.0
        val window = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        if (!window.isLive() || window.durationMs <= 0) return 0.0 to 0.0
        return 0.0 to window.durationMs / 1000.0
    }

    private fun startTicking() {
        stopTicking()
        ticker = scope.launch {
            while (true) {
                _state.update {
                    val (liveStart, liveEnd) = liveWindowSeconds()
                    it.copy(
                        positionSeconds = (controller?.currentPosition ?: 0L) / 1000.0,
                        durationSeconds = durationOrZero().takeIf { d -> d > 0 }
                            ?: it.durationSeconds,
                        // Read every tick, not once: the window slides forward
                        // with the picture, so one read at open is wrong a
                        // minute later.
                        liveStartSeconds = liveStart,
                        liveEndSeconds = liveEnd,
                    )
                }
                // Four times a second. A progress bar moving in quarter-second
                // steps looks continuous; sixty times a second is fifty-nine
                // recompositions nobody can see.
                delay(250)
            }
        }
    }

    private fun stopTicking() {
        ticker?.cancel()
        ticker = null
    }
}

@UnstableApi
class ExoVideoPlayerFactory(private val context: Context) : VideoPlayerFactory {
    override fun create(): VideoPlayer = ExoVideoPlayer(context)
}
