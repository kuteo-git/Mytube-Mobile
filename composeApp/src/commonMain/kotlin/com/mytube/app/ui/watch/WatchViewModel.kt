package com.mytube.app.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The ceiling this app asks the server for.
 *
 * 720p, and it is enforced by the **server** because on iOS it cannot be
 * enforced anywhere else: HLS plays natively there and a page has no way to
 * limit a level, so the only place the cap can live is the master playlist. The
 * number itself is from the charter — an iPhone 16e is 2532×1170, so 720p
 * already exceeds its long edge, and everything above is bytes spent on pixels
 * the screen cannot draw.
 */
const val PHONE_MAX_HEIGHT = 720

sealed interface WatchState {
    data object Loading : WatchState
    data class Failed(val message: String) : WatchState

    /**
     * A broadcast that has not begun.
     *
     * Its own state because nothing is wrong and the answer changes on its own.
     * The charter is explicit that this must not be treated as unavailable:
     * *"that means permanent, offers no retry and names a reason. This is the
     * opposite — nothing is wrong and the answer changes on its own."*
     */
    data class Upcoming(val video: Video) : WatchState

    /** Members-only, private, removed. Permanent; no retry offered. */
    data class Unavailable(val video: Video, val reason: String) : WatchState

    data class Playing(
        val video: Video,
        val playback: PlaybackState,
    ) : WatchState
}

/**
 * One video, and the player showing it.
 *
 * ## Why the player is owned here
 *
 * A decoder and an audio session are scarce — one each — so exactly one thing
 * must own them and give them back. A ViewModel's lifetime is the screen's,
 * which is precisely the span a player should live for, and `onCleared` is the
 * one callback guaranteed to run when the screen goes for good.
 *
 * Holding it in a composable instead would mean a new player on every
 * configuration change, and holding it in the container would mean two screens
 * sharing one decoder.
 */
class WatchViewModel(
    private val videoId: String,
    /** Where images live, for the artwork the lock screen draws. */
    private val mediaBaseUrl: String,
    private val videos: VideoRepository,
    private val streams: StreamRepository,
    playerFactory: VideoPlayerFactory,
) : ViewModel() {

    val player: VideoPlayer = playerFactory.create()

    private val _state = MutableStateFlow<WatchState>(WatchState.Loading)
    val state: StateFlow<WatchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // The player's own state is folded into this screen's, so a
            // composable reads one thing. Collected before loading, or the first
            // buffering report arrives with nowhere to go.
            player.state.collect { playback ->
                _state.update { current ->
                    if (current is WatchState.Playing) current.copy(playback = playback)
                    else current
                }
            }
        }
        load()
    }

    fun retry() = load()

    fun playPause() {
        val playing = (_state.value as? WatchState.Playing)?.playback?.isPlaying ?: return
        if (playing) player.pause() else player.play()
    }

    fun seekTo(seconds: Double) = player.seekTo(seconds)

    override fun onCleared() {
        player.release()
    }

    private fun load() {
        _state.value = WatchState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                val video = videos.video(videoId)
                when (val stream = streams.stream(videoId, PHONE_MAX_HEIGHT)) {
                    is Stream.Playable -> {
                        // Where the viewer left off. The server already knows —
                        // it is in the video's own user state — so the position
                        // is not something this app has to remember separately.
                        val resumeAt = video.durationSeconds * video.watchedFraction
                        player.load(
                            PlayingMedia(
                                url = stream.url,
                                title = video.title,
                                channel = video.channel.name,
                                artworkUrl = mediaBaseUrl.trimEnd('/') +
                                    "/media/" + video.thumbnailPath,
                            ),
                            if (video.isInProgress) resumeAt else 0.0,
                        )
                        player.play()
                        WatchState.Playing(video, PlaybackState())
                    }
                    is Stream.Upcoming -> WatchState.Upcoming(video)
                    is Stream.Unavailable -> WatchState.Unavailable(video, stream.reason)
                    // The server answered and offered nothing this app can play.
                    // Not an error: the charter's own answer for a video whose
                    // formats cannot be described is "no tier and no error".
                    is Stream.NothingPlayable -> WatchState.Unavailable(video, "no_tier")
                }
            }.getOrElse(::asState)
        }
    }

    private fun asState(error: Throwable): WatchState = when (error) {
        is ServerNotConfigured -> WatchState.Failed("no server")
        else -> WatchState.Failed(error.message ?: "could not play this")
    }
}
