package com.mytube.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.mytube.app.domain.model.NarrationClip
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

    /** Clips that arrived before the narrator did, replayed on connection. */
    private var pendingClips: List<NarrationClip> = emptyList()

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
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // errorCodeName rather than the message: the message is often null,
            // and a blank error line reads as the app having lost interest.
            _state.update { it.copy(error = error.errorCodeName, isBuffering = false) }
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val connected = future.get().also { it.addListener(listener) }
                controller = connected
                narrator = Narrator(context, connected).also {
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
        _state.update { PlaybackState() }
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
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun start(media: PlayingMedia, startAtSeconds: Double) {
        val player = controller ?: return
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(media.url)
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
        )
        if (startAtSeconds > 0) player.seekTo((startAtSeconds * 1000).toLong())
        player.prepare()
        player.play()
    }

    private fun durationOrZero(): Double =
        controller?.duration?.takeIf { it > 0 }?.div(1000.0) ?: 0.0

    private fun startTicking() {
        stopTicking()
        ticker = scope.launch {
            while (true) {
                _state.update {
                    it.copy(
                        positionSeconds = (controller?.currentPosition ?: 0L) / 1000.0,
                        durationSeconds = durationOrZero().takeIf { d -> d > 0 }
                            ?: it.durationSeconds,
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
