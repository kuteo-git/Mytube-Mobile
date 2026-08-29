package com.mytube.app.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/**
 * How often the viewer's position is sent to the server, in seconds of playback.
 *
 * Ten. What it feeds is Continue watching and the ranker's WATCH signal, and the
 * cost of being ten seconds stale in either is nothing a person would notice;
 * the cost of reporting on every tick is four writes a second per viewer for a
 * number that has moved by a quarter of a second.
 */
private const val PROGRESS_EVERY_SECONDS = 10.0

/**
 * How often the narration pass is asked how it is getting on.
 *
 * Three seconds. The pass produces a line every second or so, and each answer
 * carries every clip prepared so far — so a slower poll costs nothing but the
 * moment a newly ready line becomes playable, and a faster one re-sends a list
 * that has barely changed.
 */
private const val NARRATION_POLL_MILLIS = 3_000L

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
        /**
         * What to play next.
         *
         * Empty until it arrives, and its absence never delays the picture: it
         * is fetched beside the stream rather than before it, because a rail is
         * something the viewer reads after pressing play.
         */
        val upNext: List<Video> = emptyList(),
        /**
         * The narration the server has prepared, and whether it is wanted.
         *
         * `narrating` is the viewer's switch and `narration` is what exists;
         * they are separate because switching off must not throw away work the
         * server has already paid for.
         */
        val narrating: Boolean = false,
        val narration: Narration = Narration.Empty,
        /** A broadcast on air: no length, no end, and nothing to resume. */
        val isLive: Boolean = false,
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
    private val narration: NarrationRepository,
    playerFactory: VideoPlayerFactory,
) : ViewModel() {

    val player: VideoPlayer = playerFactory.create()

    private val _state = MutableStateFlow<WatchState>(WatchState.Loading)
    val state: StateFlow<WatchState> = _state.asStateFlow()

    /** The position last sent to the server, so the next report can be spaced. */
    private var lastReported = 0.0

    /** The poll watching the server's narration pass, cancelled when it ends. */
    private var narrationPoll: Job? = null

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
                reportIfDue(playback)
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

    /**
     * Turn the Vietnamese voice on or off.
     *
     * Switching on asks the server to start a pass and then polls it. Switching
     * off stops the voice and stops polling, and deliberately does **not** ask
     * the server to stop: the pass is writing translations and audio to disk
     * that the next viewing will use, and abandoning it halfway through means
     * paying for the same lines twice.
     */
    fun toggleNarration() {
        val current = _state.value as? WatchState.Playing ?: return
        val next = !current.narrating
        _state.value = current.copy(narrating = next)

        if (!next) {
            narrationPoll?.cancel()
            narrationPoll = null
            player.narrate(emptyList())
            return
        }

        narrationPoll?.cancel()
        narrationPoll = viewModelScope.launch {
            runCatching { narration.start(current.video.id) }
                .onFailure { return@launch }
            while (true) {
                val state = runCatching { narration.state(current.video.id) }.getOrNull()
                if (state != null) {
                    _state.update { now ->
                        if (now is WatchState.Playing) now.copy(narration = state) else now
                    }
                    // Handed over on every poll, not once at the end. The pass
                    // takes minutes and the first lines are ready in seconds;
                    // waiting for the whole video is minutes of silence over a
                    // video that could already be speaking.
                    if (_state.value.let { it is WatchState.Playing && it.narrating }) {
                        player.narrate(state.clips)
                    }
                    if (!state.isWorking) return@launch
                }
                delay(NARRATION_POLL_MILLIS)
            }
        }
    }

    /**
     * Move by a number of seconds, clamped to the video.
     *
     * The clamp is not decoration: seeking past the end on a media playlist
     * leaves some players buffering toward a position that will never arrive,
     * which looks exactly like a stream that has died.
     */
    fun skip(bySeconds: Double) {
        val playback = (_state.value as? WatchState.Playing)?.playback ?: return
        val target = (playback.positionSeconds + bySeconds)
            .coerceIn(0.0, maxOf(playback.durationSeconds - 1, 0.0))
        player.seekTo(target)
    }

    /**
     * Like, dislike, or take it back.
     *
     * Pressing the lit one clears it, which is what the control means: a like is
     * a statement somebody is allowed to withdraw, and a button that can only be
     * switched on is a button that punishes a mis-tap for ever.
     */
    fun react(to: Reaction) {
        val current = _state.value as? WatchState.Playing ?: return
        val next = if (current.video.reaction == to) Reaction.None else to
        // Drawn first, sent second. This is a statement about the viewer's own
        // opinion, and a control that waits for a round trip before lighting up
        // feels broken on the one screen where the wifi is already busy.
        _state.value = current.copy(video = current.video.copy(reaction = next))
        viewModelScope.launch {
            runCatching { videos.setReaction(current.video.id, next) }
                .onFailure { revert(current) }
        }
    }

    fun toggleSaved() {
        val current = _state.value as? WatchState.Playing ?: return
        val next = !current.video.saved
        _state.value = current.copy(video = current.video.copy(saved = next))
        viewModelScope.launch {
            runCatching { videos.setSaved(current.video.id, next) }
                .onFailure { revert(current) }
        }
    }

    fun toggleSubscribed() {
        val current = _state.value as? WatchState.Playing ?: return
        val channel = current.video.channel
        val next = !channel.subscribed
        _state.value = current.copy(
            video = current.video.copy(channel = channel.copy(subscribed = next)),
        )
        viewModelScope.launch {
            runCatching { videos.setSubscribed(channel.id, next) }
                .onFailure { revert(current) }
        }
    }

    /**
     * End the playback, not just this screen's hold on it.
     *
     * Separate from [close] because the two happen at different moments and mean
     * different things. Disposal must **not** stop: opening another video
     * composes the new ViewModel — which loads and plays — *before* the old
     * one's `onDispose` runs, so a stop there would kill the video that had just
     * started. Only somebody pressing the close button means stop.
     */
    fun stop() {
        report(force = true)
        player.stop()
    }

    /**
     * Give the player back, and file the last position first.
     *
     * Public, and called by whoever created this rather than left to
     * `onCleared`. The watch screen is not held by `viewModel()`: that stores a
     * ViewModel in the **activity's** store, where it survives leaving the
     * screen entirely — so every video opened would leave another instance
     * behind, each holding a live connection to the playback service, and none
     * of them would ever run this. The caller uses `remember` and a
     * `DisposableEffect`, which is only correct because the activity declares
     * `configChanges` for rotation and so is never recreated under it.
     *
     * Idempotent: pressing close reports and then disposal calls this, and the
     * second report is refused because the playhead has not moved.
     */
    fun close() {
        narrationPoll?.cancel()
        narrationPoll = null
        // The last position, before the connection goes. Without this, closing
        // the screen loses up to ten seconds of progress — and closing it is
        // precisely when somebody stops watching, so it is the report that
        // matters most.
        report(force = true)
        player.release()
    }

    override fun onCleared() = close()

    /**
     * Put back what the server would not accept.
     *
     * The optimistic draw above is only honest if a refusal undoes it. Silently
     * keeping a lit button over a like the server rejected is worse than never
     * having lit it: the next screen that reads the truth appears to have lost
     * it.
     */
    private fun revert(previous: WatchState.Playing) {
        val now = _state.value
        if (now is WatchState.Playing && now.video.id == previous.video.id) {
            _state.value = now.copy(video = previous.video)
        }
    }

    private fun reportIfDue(playback: PlaybackState) {
        if (!playback.isPlaying) return
        if (playback.positionSeconds - lastReported < PROGRESS_EVERY_SECONDS) return
        report(force = false)
    }

    private fun report(force: Boolean) {
        val current = _state.value as? WatchState.Playing ?: return
        // Nothing to report about a broadcast. Its "fraction watched" is a
        // position inside a sliding window, which means something different
        // every minute and would put a nonsense figure in Continue watching.
        if (current.isLive) return
        val playback = current.playback
        if (playback.durationSeconds <= 0) return
        // Pressing close reports and then disposal reports again, a moment
        // apart, with the playhead in the same place. The second is the same
        // fact and is not sent.
        if (playback.positionSeconds == lastReported) return
        if (!force && playback.positionSeconds - lastReported < PROGRESS_EVERY_SECONDS) return
        lastReported = playback.positionSeconds

        val position = playback.positionSeconds
        val fraction = playback.progress.toDouble()
        // Deliberately not awaited and deliberately swallowing failures: a
        // report that does not arrive costs a stale Continue watching entry,
        // and there is nothing a viewer could do about it if told.
        viewModelScope.launch {
            runCatching { videos.recordProgress(current.video.id, position, fraction) }
        }
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
                        // A broadcast has no position to resume to — its zero is
                        // an hour ago and its end is now — so it always opens at
                        // the live edge, which is where the playlist starts.
                        val resumeAt =
                            if (stream.isLive) 0.0
                            else video.durationSeconds * video.watchedFraction
                        lastReported = resumeAt
                        player.load(
                            PlayingMedia(
                                url = stream.url,
                                title = video.title,
                                channel = video.channel.name,
                                artworkUrl = mediaBaseUrl.trimEnd('/') +
                                    "/media/" + video.thumbnailPath,
                            ),
                            if (!stream.isLive && video.isInProgress) resumeAt else 0.0,
                        )
                        player.play()
                        WatchState.Playing(video, PlaybackState(), isLive = stream.isLive)
                    }
                    is Stream.Upcoming -> WatchState.Upcoming(video)
                    is Stream.Unavailable -> WatchState.Unavailable(video, stream.reason)
                    // The server answered and offered nothing this app can play.
                    // Not an error: the charter's own answer for a video whose
                    // formats cannot be described is "no tier and no error".
                    is Stream.NothingPlayable -> WatchState.Unavailable(video, "no_tier")
                }
            }.getOrElse(::asState)

            loadUpNext()
        }
    }

    /**
     * The rail, fetched after the picture rather than beside it.
     *
     * It is a second round trip that nothing on screen waits for, and running it
     * concurrently with the stream request would put it in front of the one call
     * a viewer is actually waiting on. A rail that fails simply stays empty.
     */
    private fun loadUpNext() {
        if (_state.value !is WatchState.Playing) return
        viewModelScope.launch {
            val rail = runCatching { videos.upNext(videoId) }.getOrDefault(emptyList())
            _state.update { current ->
                if (current is WatchState.Playing) current.copy(upNext = rail) else current
            }
        }
    }

    private fun asState(error: Throwable): WatchState = when (error) {
        is ServerNotConfigured -> WatchState.Failed("no server")
        else -> WatchState.Failed(error.message ?: "could not play this")
    }
}
