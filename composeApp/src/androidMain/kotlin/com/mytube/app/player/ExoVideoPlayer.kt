package com.mytube.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mytube.app.domain.repository.PlaybackState
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
 * ExoPlayer behind the port.
 *
 * HLS needs no configuration here: `media3-exoplayer-hls` is on the classpath,
 * and ExoPlayer picks the source type from the URL. The ladder the server writes
 * is then ExoPlayer's to climb — it does adaptive selection itself, which is the
 * whole reason the server publishes a real ladder rather than one rendition.
 */
@UnstableApi
class ExoVideoPlayer(context: Context) : VideoPlayer {

    /**
     * Exposed so the Compose surface can attach to it.
     *
     * A leak in the abstraction, and a deliberate one: `PlayerView` takes an
     * `androidx.media3.common.Player`, and nothing in common code can hand it
     * one. The alternative is a parallel surface API that exists only to avoid
     * admitting this, which would be worse.
     */
    val exo: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var ticker: Job? = null

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
                    durationSeconds = exo.duration.takeIf { d -> d > 0 }?.div(1000.0) ?: 0.0,
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
        exo.addListener(listener)
    }

    override fun load(url: String, startAtSeconds: Double) {
        _state.update { PlaybackState() }
        exo.setMediaItem(MediaItem.fromUri(url))
        if (startAtSeconds > 0) exo.seekTo((startAtSeconds * 1000).toLong())
        exo.prepare()
    }

    override fun play() {
        exo.play()
    }

    override fun pause() {
        exo.pause()
    }

    override fun seekTo(seconds: Double) {
        exo.seekTo((seconds * 1000).toLong())
        // Written straight away rather than waited for. A seek bar that snaps
        // back to the old position for a frame before jumping is the thing that
        // makes dragging one feel broken.
        _state.update { it.copy(positionSeconds = seconds) }
    }

    override fun release() {
        stopTicking()
        exo.removeListener(listener)
        exo.release()
    }

    private fun startTicking() {
        stopTicking()
        ticker = scope.launch {
            while (true) {
                _state.update {
                    it.copy(
                        positionSeconds = exo.currentPosition / 1000.0,
                        durationSeconds = exo.duration.takeIf { d -> d > 0 }?.div(1000.0)
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
