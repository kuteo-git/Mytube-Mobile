package com.mytube.app.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Comment
import com.mytube.app.domain.model.DEFAULT_DUCK_LEVEL
import com.mytube.app.domain.model.DEFAULT_VOICE_LEVEL
import com.mytube.app.domain.model.Narration
import com.mytube.app.domain.model.SubtitleTrack
import com.mytube.app.domain.model.SubtitleCue
import com.mytube.app.domain.model.Reaction
import com.mytube.app.domain.model.Stream
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.PlaybackState
import com.mytube.app.domain.repository.PlayingMedia
import com.mytube.app.domain.repository.PlayingSubtitle
import com.mytube.app.domain.repository.NarrationRepository
import com.mytube.app.domain.repository.PreferencesRepository
import com.mytube.app.domain.repository.StreamRepository
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.domain.repository.VideoPlayerFactory
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.home.imageModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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

/**
 * How often the video is re-read while waiting for its caption tracks, and how
 * long that is worth doing for.
 *
 * The gateway fetches subtitles **when play is pressed** — `handleStream` kicks
 * off `fetchSubtitlesOnly` in the background — so a video nobody has opened
 * before answers `/api/videos/{id}` with an empty `subtitles` list, and this app
 * read that once and believed it for the rest of the sitting. That is the whole
 * of two faults reported together: no CC button on a new video, and narration
 * doing nothing until it was switched off and on again. Switching it off and on
 * "fixed" it only because by then the tracks had arrived.
 *
 * Four seconds apart for a minute. Long enough for an upstream fetch of a
 * caption file, and bounded rather than endless: a video that genuinely has no
 * subtitles must stop being asked about, or every video without them costs a
 * request a second for as long as it plays.
 */
private const val SUBTITLE_POLL_MILLIS = 4_000L
private const val SUBTITLE_POLL_ATTEMPTS = 15

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
        /**
         * Whether narration can be offered for this broadcast.
         *
         * Only a broadcast has an answer here, and it is the server's: some
         * streams publish captions and some do not. Recorded videos are handled
         * by [canNarrate] below, which is what every screen reads.
         */
        val hasLiveCaptions: Boolean = false,
        val comments: List<Comment> = emptyList(),
        /**
         * The comments are still on their way.
         *
         * True from the first request rather than only while an import runs, so
         * the section can draw the shape of what is coming for the whole wait.
         * It used to cover the import alone, which meant the *usual* case — a
         * video whose comments the catalogue already holds — showed an empty
         * heading reading "0 comments" until they landed.
         */
        val loadingComments: Boolean = false,
        /**
         * The comments have been asked for and answered.
         *
         * Separate from `comments.isEmpty()` because those are two different
         * answers: a video nobody has commented on and a video nobody has asked
         * about. It is also what stops a second fetch when the section is folded
         * away and opened again.
         */
        val commentsLoaded: Boolean = false,
        /** The rail's request is still out. It is fetched after the picture. */
        val loadingUpNext: Boolean = false,
        /** The rail is folded away, which is a per-video preference nobody stores. */
        val railCollapsed: Boolean = false,
        /** The rail is filtered to this video's own channel. */
        val railChannelOnly: Boolean = false,
        /** The track being shown, or empty for none. */
        val subtitleLanguage: String = "",
        /**
         * The cues of that track, when this player does not draw them itself.
         *
         * Empty on Android, always: ExoPlayer is handed the file and renders
         * the captions on the picture. On iOS AVPlayer will not take a caption
         * file outside the HLS manifest, so the words are drawn by the screen —
         * see `VideoPlayer.rendersSubtitles`.
         */
        val subtitleCues: List<SubtitleCue> = emptyList(),
        /**
         * Whether reaching the end should advance to the next video.
         *
         * Per device and per sitting, not stored: it is a decision about *this*
         * evening, and a phone that remembers it for a fortnight starts playing
         * on its own the next time somebody opens one video deliberately.
         */
        val autoplay: Boolean = false,
        /**
         * How loud the voice is, and what the video drops to under it.
         *
         * In the state rather than read from the store by the sheet, because
         * the sliders have to move while a finger is on them — a control that
         * waits for a write to disk before it redraws is a control that sticks.
         */
        val voiceLevel: Float = DEFAULT_VOICE_LEVEL,
        val duckLevel: Float = DEFAULT_DUCK_LEVEL,
        /**
         * What pressing next plays, or empty when there is nowhere to go.
         *
         * Computed once here rather than by the screen reaching into `upNext`,
         * because the answer is no longer always the rail's first entry: a video
         * opened from a channel follows *that* channel's list, in the order the
         * channel page was showing. See [nextId].
         */
        val nextId: String = "",
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
    videoId: String,
    /**
     * Whether this video was arrived at by advancing rather than by being
     * chosen, in which case it starts at zero.
     *
     * **No default, deliberately.** It had one, the single call site was not
     * updated to pass it, and the compiler said nothing — so "next" resumed the
     * following video at twelve minutes in, which is the precise behaviour the
     * flag exists to prevent. A default here buys one short call site and pays
     * for it with a fault nothing can catch.
     */
    startAtBeginning: Boolean,
    /**
     * Whether to start playing as soon as the stream is ready.
     *
     * False for exactly one case, and it is the reason this exists: the video
     * restored from the last session. Reopening the app puts the miniplayer back
     * with the video loaded and *stopped* — sound starting on its own because
     * somebody unlocked their phone is the behaviour nobody wants and every app
     * that does it gets complained about. Pressing play then resumes from where
     * they left off, which the server already knows.
     *
     * **No default**, following [startAtBeginning] and for the same reason: a
     * default here buys one short call site and pays for it with a video that
     * silently plays, or silently does not, at a call site nobody updated.
     */
    autoPlay: Boolean,
    /**
     * Advance to this video, because the last one played to its end.
     *
     * A callback rather than state the screen watches, because what happens next
     * is navigation — it belongs to whoever owns the route, not to the video
     * that just finished. It fires **only** when autoplay is on and there is
     * something to advance to, so the caller has no condition to re-check and
     * the two cannot disagree about when a video ends.
     */
    private val onFinished: (String) -> Unit,
    /** Where images live, for the artwork the lock screen draws. */
    private val mediaBaseUrl: String,
    private val videos: VideoRepository,
    private val streams: StreamRepository,
    private val narration: NarrationRepository,
    private val preferences: PreferencesRepository,
    /**
     * The list this video was opened from, in the order it was shown.
     *
     * Empty when the video was opened from somewhere with no order to it — the
     * feed, a search, the miniplayer's rail. When it is not empty, *this* is
     * what next means, which is the web app's rule: a channel page sorted by
     * Popular and then played through has to stay in that order, or sorting was
     * only ever a way of finding one video to leave the list by.
     */
    openedFrom: List<QueueItem> = emptyList(),
    playerFactory: VideoPlayerFactory,
) : ViewModel() {

    /**
     * The list, with every row this sitting has written marked as written.
     *
     * A `var`, and that is the whole of it: the queue is a *copy* of what the
     * catalogue holds, `ensureInCatalogue` is the thing that changes what the
     * catalogue holds, and a copy nobody updates is a copy that is wrong from
     * the first write onwards. It used to be the constructor `val`, so the row
     * this sitting had just written still read `inLibrary = false` for the rest
     * of the sitting — and pressing retry, or leaving a video and coming back
     * to it, asked the gateway to write it again. One call is one full metadata
     * fetch upstream, which is the cost `ChannelViewModel.openVideo` marks its
     * own row to avoid; this is that same bookkeeping for the rows reached
     * after it.
     */
    private var queue: List<QueueItem> = openedFrom

    /**
     * Which video this is, and it can change without this object being replaced.
     *
     * It used to be a constructor `val`, one instance per video, and that is
     * what broke autoplay with the screen off. Advancing meant a **new**
     * `WatchViewModel`, and a new one is built by `remember` — which is
     * composition, and composition on iOS stops when the app leaves the
     * foreground. So the sound ran to the end of a video and stopped, and the
     * next one appeared only when somebody unlocked the phone and looked.
     *
     * Background audio is the one thing §1 of the charter says this app exists
     * for, so the sitting has to be able to move on without a frame being drawn.
     */
    private var videoId: String = videoId

    /** See [advanceTo]. Set per video, not once per object. */
    private var startAtBeginning: Boolean = startAtBeginning
    private var autoPlay: Boolean = autoPlay

    /** What the app's own session should be showing. */
    val currentVideoId: String get() = videoId

    val player: VideoPlayer = playerFactory.create()

    private val _state = MutableStateFlow<WatchState>(WatchState.Loading)
    val state: StateFlow<WatchState> = _state.asStateFlow()

    /** The position last sent to the server, so the next report can be spaced. */
    private var lastReported = 0.0

    /** The poll watching the server's narration pass, cancelled when it ends. */
    private var narrationPoll: Job? = null

    /** The end is reported on every tick; this makes the advance happen once. */
    private var advanced = false

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
                advanceIfFinished(playback)
            }
        }
        load()
    }

    /**
     * The end of a video is where autoplay happens.
     *
     * Nothing called `onFinished` before this: the callback was declared, the
     * switch was drawn, the preference was stored, and no code anywhere noticed
     * a video ending. Autoplay was a dead control on both platforms.
     *
     * Guarded by `advanced` rather than by the state alone, because the end is
     * reported on every tick once it arrives — without it the rail's first
     * entry would be opened four times a second.
     */
    private fun advanceIfFinished(playback: PlaybackState) {
        if (!playback.hasEnded || advanced) return
        val current = _state.value as? WatchState.Playing ?: return
        if (!current.autoplay) return
        // A broadcast has no end to reach, and nothing sensible to advance to.
        if (current.isLive) return
        if (current.nextId.isEmpty()) return
        advanced = true
        // Loaded here, **not** by whoever owns the route.
        //
        // The callback still fires, because the session and the trail are the
        // route's business — but it is told after the fact, and it may be told
        // while nothing is being composed at all. What must not depend on a
        // frame is the loading of the next video, and that is this line.
        advanceTo(current.nextId, fromTheStart = true)
        onFinished(current.nextId)
    }

    /**
     * Play a different video without replacing this object.
     *
     * Called from two places and they are the same act: the end of a video with
     * autoplay on, and the route telling this that its session moved on. The
     * second is idempotent — it checks the id first — so the two cannot load the
     * same video twice when composition catches up with a background advance.
     */
    fun advanceTo(next: String, fromTheStart: Boolean) {
        if (next.isEmpty() || next == videoId) return
        // The finished video's last position, before its id is gone. Same
        // reasoning as `close()`: the moment somebody stops watching something
        // is the report that matters most.
        report(force = true)
        narrationPoll?.cancel()
        narrationPoll = null
        videoId = next
        // Advancing means "play me the next thing", so it starts at zero;
        // *choosing* one means "play me this", which resumes where the server
        // says the household left it. The distinction is the caller's, and it is
        // the one that stops "previous" restarting a video somebody was half way
        // through.
        startAtBeginning = fromTheStart
        // Always. Every path into this is somebody asking for a video to play.
        autoPlay = true
        advanced = false
        lastReported = 0.0
        load()
    }

    fun retry() = load()

    fun playPause() {
        val playing = (_state.value as? WatchState.Playing)?.playback?.isPlaying ?: return
        if (playing) player.pause() else player.play()
    }

    fun seekTo(seconds: Double) {
        player.seekTo(seconds)
        retargetNarration(seconds)
    }

    /**
     * Tell a running pass where the viewer went.
     *
     * The server works from one end of the video to the other, and a seek makes
     * that the wrong order: somebody who jumps to 20:00 is waiting for 20:00
     * while the pass is still translating minute three. Asking again moves the
     * front of the queue.
     *
     * **The threshold is the server's, not this one's.** It answers a request
     * for a place it is already working from by doing nothing, so a nudge of the
     * bar costs one call and no work — and putting the number here as well
     * would be two rules that can disagree about what "far" means.
     *
     * Fire and forget: a failed retarget leaves a pass running in the order it
     * had, which is the state this is an improvement on rather than a
     * requirement for.
     */
    private fun retargetNarration(seconds: Double) {
        val current = _state.value as? WatchState.Playing ?: return
        if (!current.narrating) return
        viewModelScope.launch {
            runCatching { narration.start(current.video.id, seconds) }
        }
    }

    /**
     * Turn the Vietnamese voice on or off.
     *
     * Switching on asks the server to start a pass and then polls it. Switching
     * off stops the voice and stops polling, and deliberately does **not** ask
     * the server to stop: the pass is writing translations and audio to disk
     * that the next viewing will use, and abandoning it halfway through means
     * paying for the same lines twice.
     */
    /**
     * Show a caption track, or turn them off.
     *
     * The empty string is off, deliberately the same call: "show nothing" and
     * "stop showing" are one state, and two methods for it would be two states
     * that can disagree.
     */
    fun selectSubtitles(language: String) {
        val current = _state.value as? WatchState.Playing ?: return
        val next = if (current.subtitleLanguage == language) "" else language
        _state.value = current.copy(subtitleLanguage = next, subtitleCues = emptyList())
        player.showSubtitles(next)
        loadCues(next)
        viewModelScope.launch { runCatching { preferences.setSubtitleLanguage(next) } }
    }

    /**
     * Fetch and parse the chosen track, for players that do not draw captions.
     *
     * A failure is swallowed on purpose: captions are the one thing on this
     * screen that nothing else depends on, and a video that plays with no
     * subtitles is a far better outcome than an error over a working picture.
     * The menu still shows the track as chosen, which is honest — it is chosen,
     * and the file did not arrive.
     */
    private fun loadCues(language: String) {
        if (player.rendersSubtitles) return
        val current = _state.value as? WatchState.Playing ?: return
        val track = current.video.subtitles.firstOrNull { it.language == language }
        if (language.isEmpty() || track == null) return
        // A track with no file is one the player renders — a broadcast's, which
        // lives in the manifest. There is nothing here to fetch.
        if (track.url.isEmpty()) return
        viewModelScope.launch {
            val cues = runCatching {
                videos.subtitleCues(mediaBaseUrl.trimEnd('/') + track.url)
            }.getOrDefault(emptyList())
            // Re-read rather than closing over `current`: the video may have
            // been changed, or the track switched off, while this was in
            // flight, and writing then would put one video's captions over
            // another's picture.
            val now = _state.value as? WatchState.Playing ?: return@launch
            if (now.subtitleLanguage == language && now.video.id == current.video.id) {
                _state.value = now.copy(subtitleCues = cues)
            }
        }
    }

    /**
     * What next means for this video.
     *
     * The queue wins when this video is in it, which is what makes a channel
     * sorted by Popular play through in that order. Falling back to the rail —
     * rather than to nothing — matters when a queue runs out: the last video of
     * a channel page still has somewhere to go, which is what the rail is for.
     */
    private fun nextId(upNext: List<Video>): String {
        val here = queue.indexOfFirst { it.id == videoId }
        if (here >= 0 && here + 1 < queue.size) return queue[here + 1].id
        return upNext.firstOrNull()?.id.orEmpty()
    }

    /**
     * Apply the levels chosen in Settings to the player.
     *
     * Apply, not *set*: nothing is written to disk here. Settings owns those
     * values and persists them, and this exists because the sliders can be moved
     * while a video is already playing in the miniplayer — the player would
     * otherwise keep the levels it was handed when the video opened, and the
     * change would appear to do nothing until the next video.
     */
    fun applyNarrationLevels(voice: Float, duck: Float) {
        player.setNarrationLevels(voice, duck)
        _state.update { current ->
            if (current is WatchState.Playing) {
                current.copy(voiceLevel = voice, duckLevel = duck)
            } else {
                current
            }
        }
    }

    fun toggleAutoplay() {
        val current = _state.value as? WatchState.Playing ?: return
        val next = !current.autoplay
        _state.value = current.copy(autoplay = next)
        viewModelScope.launch { runCatching { preferences.setAutoplay(next) } }
    }

    fun toggleRail() = _state.update {
        if (it is WatchState.Playing) it.copy(railCollapsed = !it.railCollapsed) else it
    }

    /**
     * Narrow the rail to this channel, or widen it again.
     *
     * Re-asks the server rather than filtering what is already held: the
     * endpoint takes a channel and returns a *different ranking* for it, not a
     * subset of the same one, so filtering here would show the wrong videos in
     * the wrong order and only look right.
     */
    fun filterRail(channelOnly: Boolean) {
        val current = _state.value as? WatchState.Playing ?: return
        if (current.railChannelOnly == channelOnly) return
        // nextId goes with the rail it came from. Left behind, "next" would
        // point into a list this screen is no longer showing.
        _state.value = current.copy(
            railChannelOnly = channelOnly,
            upNext = emptyList(),
            nextId = nextId(emptyList()),
            loadingUpNext = true,
        )
        loadUpNext(if (channelOnly) current.video.channel.id else "")
    }

    fun toggleNarration() {
        val current = _state.value as? WatchState.Playing ?: return
        val next = !current.narrating
        _state.value = current.copy(narrating = next)

        viewModelScope.launch { runCatching { preferences.setNarration(next) } }

        if (!next) {
            narrationPoll?.cancel()
            narrationPoll = null
            player.narrate(emptyList())
            return
        }

        startNarration()
    }

    /**
     * Ask the server for this video's narration, and follow the pass.
     *
     * Separate from the switch because two things call it: somebody pressing the
     * switch, and a device that had it on last time. Folded into `toggle`, the
     * remembered case would have had to flip a boolean it already knew the value
     * of just to reach this code.
     */
    private fun startNarration() {
        val current = _state.value as? WatchState.Playing ?: return
        _state.value = current.copy(narrating = true)

        narrationPoll?.cancel()
        narrationPoll = viewModelScope.launch {
            // From where the playhead is, not from zero. A pass takes minutes,
            // and somebody who turns narration on twenty minutes into a film is
            // waiting for minute twenty — the server speaks from there to the
            // end and then goes back for the beginning, so nothing is lost.
            //
            // Read here rather than passed in, because this is called from two
            // places and one of them is a remembered preference firing as the
            // video opens, where the position is whatever was resumed to.
            val from = current.playback.positionSeconds
            runCatching { narration.start(current.video.id, from) }
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
     * Move by a number of seconds, clamped to whatever this video has.
     *
     * The arithmetic is `PlaybackState`'s, beside the bar's own `seekTarget`:
     * a jump and a drag are the same question about where a position may land,
     * and answering it twice is how a broadcast ended up with a jump that went
     * to zero while the bar worked. @see PlaybackState.skipTarget
     */
    fun skip(bySeconds: Double) {
        val playback = (_state.value as? WatchState.Playing)?.playback ?: return
        val target = playback.skipTarget(bySeconds)
        player.seekTo(target)
        retargetNarration(target)
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

    /**
     * Redraw the Save pill after the sheet has applied its changes.
     *
     * The pill no longer writes the pinned bit itself — pressing it opens the
     * sheet, which asks *which* collections this belongs in and applies the
     * difference. So this sends nothing: the request has already happened, and
     * repeating it here would be a second writer of one fact.
     */
    fun markSaved(saved: Boolean) {
        val current = _state.value as? WatchState.Playing ?: return
        _state.value = current.copy(video = current.video.copy(saved = saved))
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
        stopNarrationPass()
        player.stop()
    }

    /**
     * Tell the server to stop translating and speaking this video.
     *
     * Only from [stop], which is the close button. Switching narration off does
     * not do this — the pass is writing lines to disk that the next viewing
     * would otherwise pay for again — and neither does shrinking to the
     * miniplayer, which is still watching. Closing is the one act that means
     * "done with this video", and it is the line the player already draws
     * between `stop` and `release`.
     *
     * `NonCancellable`, because this is sent at the exact moment the screen
     * goes away. `viewModelScope` survives that today only because this
     * ViewModel is held in a `remember` and nothing calls `clear()` on it —
     * which is a fact about the *caller* and the wrong thing for a request to
     * depend on. Without it, the day this moves into a ViewModelStore the
     * server carries on spending and nothing says why.
     */
    private fun stopNarrationPass() {
        val current = _state.value as? WatchState.Playing ?: return
        if (!current.narrating) return
        narrationPoll?.cancel()
        narrationPoll = null
        viewModelScope.launch(NonCancellable) {
            runCatching { narration.stop(current.video.id) }
        }
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

    /**
     * Write this video's row before anything asks the catalogue about it.
     *
     * Only for a queue entry that says it needs it — a **channel page's** rows
     * come from YouTube and most have no row at all, which is why pressing next
     * used to answer *"gateway answered 404 for /api/videos/<id>"* for a video
     * that was fine. `ChannelViewModel.openVideo` does exactly this for the row
     * somebody presses; this is the same three steps for every row reached
     * *after* it, which is the half that was missing.
     *
     * - **The address, not an id formatted into a URL.** The gateway's
     *   `POST /api/videos/external` takes one and the queue already carries it.
     * - **An empty id back is a refusal wearing a success's clothes**, so the
     *   id is left alone and the load fails the way it would have anyway —
     *   which is honest: there is genuinely nothing to play.
     * - **A row already in the library is not written again.** One call is one
     *   full metadata fetch upstream, and this runs in front of a viewer who is
     *   waiting for a picture.
     *
     * Not caught here: a failure belongs to the same `runCatching` the load
     * already has, because a row that could not be written is a video that
     * cannot play, and that is the message the screen is for.
     */
    private suspend fun ensureInCatalogue() {
        val entry = queue.firstOrNull { it.id == videoId } ?: return
        if (entry.inLibrary || entry.sourceUrl.isEmpty()) return
        val written = videos.ensureExternal(entry.sourceUrl)
        if (written.isEmpty()) return
        // The queue is rewritten before `videoId` is, and both halves matter.
        //
        // Marking it written is what stops the next visit to this row asking
        // the gateway a second time. Carrying the *new* id is what keeps the
        // row findable: `nextId` looks this video up in the queue by id, so an
        // id that changed here with the queue left alone would match nothing,
        // and the rest of a channel sorted by Popular would quietly follow the
        // recommendation rail instead of the order somebody chose. The gateway
        // answers with the id it was asked about today; this does not depend on
        // it.
        queue = queue.map {
            if (it.id == entry.id) {
                QueueItem(id = written, sourceUrl = it.sourceUrl, inLibrary = true)
            } else {
                it
            }
        }
        videoId = written
    }

    private fun load() {
        _state.value = WatchState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                ensureInCatalogue()
                val video = videos.video(videoId)
                when (val stream = streams.stream(videoId, PHONE_MAX_HEIGHT)) {
                    is Stream.Playable -> {
                        // Where the viewer left off. The server already knows —
                        // it is in the video's own user state — so the position
                        // is not something this app has to remember separately.
                        // The server's own number, not the fraction times the
                        // duration. See `Video.watchPositionSeconds` for why
                        // those are not the same thing.
                        //
                        // A broadcast has no position to resume to — its zero is
                        // an hour ago and its end is now — so it always opens at
                        // the live edge, which is where the playlist starts. And
                        // `startAtBeginning` is set when this video was arrived
                        // at by pressing next: advancing means "play me the next
                        // thing", and dropping somebody into the middle of a
                        // track they did not pick reads as a glitch.
                        val resumeAt = when {
                            stream.isLive || startAtBeginning -> 0.0
                            else -> video.watchPositionSeconds.toDouble()
                        }
                        lastReported = resumeAt
                        player.load(
                            PlayingMedia(
                                url = stream.url,
                                title = video.title,
                                channel = video.channel.name,
                                // `imageModel`, not `/media/` pasted in front
                                // of the path. A broadcast's thumbnail is an
                                // absolute address at YouTube — the catalogue
                                // never scanned a file for a stream on air — and
                                // prefixing one produced
                                // `…/media/https://i.ytimg.com/…`, which the
                                // gateway answers 404 for. Measured: the pasted
                                // address 404, the thumbnail itself 200, and a
                                // live video played with an empty square on the
                                // lock screen.
                                //
                                // Every card in every list has asked this
                                // question through `imageModel` since the
                                // channel page needed it. This was the one
                                // caller that answered it itself.
                                artworkUrl = imageModel(mediaBaseUrl, video.thumbnailPath),
                                // Every track, attached now. Both platforms bind
                                // text to the media item, so adding one later
                                // means a new item and a restarted video.
                                subtitles = video.subtitles.map {
                                    PlayingSubtitle(
                                        url = mediaBaseUrl.trimEnd('/') + it.url,
                                        language = it.language,
                                        label = it.label,
                                    )
                                },
                                isLive = stream.isLive,
                            ),
                            // `isInProgress` still gates it: a video watched to
                            // the end has its position saved near the end, so
                            // resuming would run out immediately.
                            if (video.isInProgress) resumeAt else 0.0,
                        )
                        if (autoPlay) player.play()

                        // The device's own answers, read once the video is
                        // known. Applied here rather than at construction
                        // because two of the three depend on what this video
                        // actually has: a remembered language that this video
                        // does not carry means no subtitles, not the nearest
                        // one.
                        // A broadcast's captions are inside the HLS manifest
                        // rather than beside the video on disk, so they never
                        // reach `video.subtitles` — and both the CC control and
                        // the remembered language are read from that list. The
                        // track is added here, with no URL because there is no
                        // file: the player renders it, and `loadCues` skips a
                        // track with nothing to fetch.
                        //
                        // Composed *before* the language is decided, and that
                        // order is the fix rather than a tidy-up: it used to be
                        // added inside the state below, so a broadcast's list
                        // was still empty at this point and a viewer who had
                        // asked for English got no captions on a stream that
                        // carried them.
                        val playable =
                            if (stream.hasLiveCaptions) {
                                video.copy(subtitles = video.subtitles + SubtitleTrack(
                                    language = stream.liveCaptionsLanguage,
                                    label = stream.liveCaptionsLanguage.uppercase(),
                                    url = "",
                                    generated = true,
                                ))
                            } else {
                                video
                            }

                        val wantedLanguage = preferences.subtitleLanguage()
                        val language =
                            if (playable.subtitles.any { it.language == wantedLanguage }) {
                                wantedLanguage
                            } else {
                                ""
                            }
                        if (language.isNotEmpty()) player.showSubtitles(language)

                        // The device's levels, told to the player before a
                        // single line exists. The player holds them until a
                        // narrator is built, which is why this can run here
                        // rather than being deferred to the first clip.
                        val voiceLevel = preferences.voiceLevel()
                        val duckLevel = preferences.duckLevel()
                        player.setNarrationLevels(voiceLevel, duckLevel)

                        WatchState.Playing(
                            video = playable,
                            playback = PlaybackState(),
                            isLive = stream.isLive,
                            hasLiveCaptions = stream.hasLiveCaptions,
                            subtitleLanguage = language,
                            autoplay = preferences.autoplay(),
                            voiceLevel = voiceLevel,
                            duckLevel = duckLevel,
                            // Deliberately **not** started here. The switch
                            // records what somebody wants; starting a server
                            // pass is what `startNarration` does, and doing both
                            // from one place is how a preference becomes an
                            // action nobody asked for on this video.
                            narrating = false,
                        )
                    }
                    is Stream.Upcoming -> WatchState.Upcoming(video)
                    is Stream.Unavailable -> WatchState.Unavailable(video, stream.reason)
                    // The server answered and offered nothing this app can play.
                    // Not an error: the charter's own answer for a video whose
                    // formats cannot be described is "no tier and no error".
                    is Stream.NothingPlayable -> WatchState.Unavailable(video, "no_tier")
                }
            }.getOrElse(::asState)

            loadUpNext("")
            fillDescription()
            // Before the narration decision below, and deliberately: on a video
            // nobody has opened before there are no caption tracks yet, and the
            // pass this app is about to ask for reads one.
            awaitSubtitles()
            // After the state exists, not beside the track selection above:
            // `loadCues` reads the current Playing state to find the track's
            // URL, and at that point there is not one yet.
            (_state.value as? WatchState.Playing)?.let { loadCues(it.subtitleLanguage) }
            // A remembered preference does not start a broadcast narrating,
            // even one that could be.
            //
            // A recorded pass ends; a broadcast's does not, so leaving the
            // switch on from yesterday would translate and speak for as long as
            // the stream stayed open. That is a real cost to spend on somebody
            // who has not asked for it *here* — so a broadcast is narrated only
            // by pressing the switch, which is one press and says what it costs
            // by being deliberate.
            if (preferences.narration() && _state.value.let {
                    it is WatchState.Playing && !it.isLive
                }
            ) {
                startNarration()
            }
        }
    }

    /**
     * The description, fetched when the row has none.
     *
     * Most of the library has none: only the download path ever wrote one, so a
     * video that arrived through a scan carries an empty string. Measured
     * against the running gateway, 0 of the feed's first 24 videos had a
     * description and 2761 of 43295 rows in the catalogue did — 6.4%, which is
     * the share that has been downloaded. The box under the picture drew the
     * view count and the date and nothing else, and there was no way to tell
     * that from a video whose author wrote none.
     *
     * ## Why this asks, rather than the server filling it in
     *
     * The server has a backfill that walks the catalogue and would do this
     * eventually — bounded, paced, and prioritised, because that library has
     * been blocked once by YouTube for asking too often. A video somebody has
     * just opened cannot wait for a scheduled pass to reach it, and one open is
     * the cheapest possible target: exactly the videos that get watched.
     *
     * ## Why it is safe to call every time
     *
     * The refusal lives on the server, which is the side that can see the row:
     * a video that already has a description is answered from the database in
     * milliseconds without touching upstream. A guard here as well would be a
     * second place deciding the same thing, and this side's copy of the row is
     * the one that goes stale.
     *
     * A failure is silence. The page is whole without this — the video plays,
     * up next is there — which is the judgement `loadComments` already makes for
     * the other thing on this screen nothing depends on.
     *
     * Its own coroutine, so it does not sit in front of `awaitSubtitles`: this
     * is a round trip to YouTube and the caller's block has work behind it that
     * the viewer is actually waiting on.
     */
    private fun fillDescription() {
        val current = _state.value as? WatchState.Playing ?: return
        if (current.video.description.isNotEmpty()) return
        val id = current.video.id

        viewModelScope.launch {
            runCatching { videos.refreshMetadata(id) }.getOrElse { return@launch }
            val filled = runCatching { videos.video(id) }.getOrNull() ?: return@launch
            if (filled.description.isEmpty()) return@launch

            _state.update { state ->
                // The id is checked because the fetch outlives the video: a
                // pass takes seconds and pressing next takes one, and writing
                // this answer onto whatever is playing now would print one
                // video's description under another's title.
                if (state is WatchState.Playing && state.video.id == id) {
                    state.copy(video = state.video.copy(description = filled.description))
                } else {
                    state
                }
            }
        }
    }

    /**
     * The comments, fetched when the section is opened.
     *
     * Not on arrival any more. The section is folded away when a video opens, so
     * fetching then — and running an import for a video nobody has opened — was
     * a request, and sometimes a scrape, for something nothing on screen shows.
     *
     * A failure leaves the list empty rather than raising: comments are the one
     * thing on this screen nothing depends on, and the server charter records
     * the web app's lesson about that — a refusal here once turned the console
     * red over a video that played perfectly.
     */
    fun loadComments() {
        val current = _state.value as? WatchState.Playing ?: return
        // Once per video. The section can be folded and unfolded all day; what
        // it costs the second time is nothing.
        if (current.commentsLoaded || current.loadingComments) return
        _state.update { c ->
            if (c is WatchState.Playing) c.copy(loadingComments = true) else c
        }
        viewModelScope.launch {
            var found = runCatching { videos.comments(videoId) }.getOrDefault(emptyList())

            if (found.isEmpty()) {
                // The catalogue holds none for a video nobody has opened, so
                // without this every video shows an empty section for ever.
                // Once, and never on a retry loop: upstream declining is a real
                // answer, and asking again on every visit is a request per view
                // to an endpoint that can only say no.
                runCatching { videos.importComments(videoId) }
                found = runCatching { videos.comments(videoId) }.getOrDefault(emptyList())
            }

            _state.update { current ->
                if (current is WatchState.Playing) {
                    current.copy(
                        comments = found,
                        loadingComments = false,
                        commentsLoaded = true,
                    )
                } else {
                    current
                }
            }
        }
    }

    /**
     * The rail, fetched after the picture rather than beside it.
     *
     * It is a second round trip that nothing on screen waits for, and running it
     * concurrently with the stream request would put it in front of the one call
     * a viewer is actually waiting on. A rail that fails simply stays empty.
     */
    private fun loadUpNext(channelId: String) {
        if (_state.value !is WatchState.Playing) return
        _state.update { c ->
            if (c is WatchState.Playing) c.copy(loadingUpNext = true) else c
        }
        viewModelScope.launch {
            val rail = runCatching { videos.upNext(videoId, channelId) }
                .getOrDefault(emptyList())
            _state.update { current ->
                if (current is WatchState.Playing) {
                    current.copy(upNext = rail, nextId = nextId(rail), loadingUpNext = false)
                } else {
                    current
                }
            }
        }
    }

    /**
     * Wait for the caption tracks the gateway fetches once play is pressed.
     *
     * The video was read before the stream request, so on anything nobody has
     * opened before its `subtitles` list is empty — and this app believed that
     * for the whole sitting. Two reported faults were one cause: no CC button
     * appeared on a new video, and the voice did nothing until it was switched
     * off and on again, which "worked" only because the tracks had arrived by
     * then.
     *
     * Bounded, and it stops the moment anything arrives. A video that truly
     * carries no captions is a real answer, and asking about it for ever would
     * be a request every four seconds for the length of a film.
     *
     * `suspend`, and awaited by [load], because the narration decision below it
     * depends on the answer: a pass started against a video with no caption file
     * is a pass with nothing to read.
     */
    private suspend fun awaitSubtitles() {
        val current = _state.value as? WatchState.Playing ?: return
        if (current.video.subtitles.isNotEmpty()) return
        // A broadcast is still being spoken; there is no caption file coming.
        if (current.isLive) return

        repeat(SUBTITLE_POLL_ATTEMPTS) {
            delay(SUBTITLE_POLL_MILLIS)
            val now = _state.value as? WatchState.Playing ?: return
            // Somebody moved on, or found a track another way.
            if (now.video.id != current.video.id) return
            if (now.video.subtitles.isNotEmpty()) return

            val fresh = runCatching { videos.video(current.video.id) }.getOrNull() ?: return@repeat
            if (fresh.subtitles.isEmpty()) return@repeat

            // Only the tracks are taken. The rest of the fresh copy would
            // overwrite the like, the save and the subscription this screen has
            // drawn optimistically since it loaded — putting the server's older
            // answer back over the viewer's own action.
            val settled = _state.value as? WatchState.Playing ?: return
            if (settled.video.id != current.video.id) return
            _state.value = settled.copy(
                video = settled.video.copy(subtitles = fresh.subtitles),
            )
            applyRememberedTrack()
            return
        }
    }

    /**
     * Show the remembered language, now that the video admits to having it.
     *
     * Only when nothing is showing already: a viewer who chose a track while
     * this was waiting has said something more recent than the stored
     * preference, and overriding it would undo a deliberate choice.
     */
    private suspend fun applyRememberedTrack() {
        val current = _state.value as? WatchState.Playing ?: return
        if (current.subtitleLanguage.isNotEmpty()) return
        val wanted = runCatching { preferences.subtitleLanguage() }.getOrDefault("")
        if (wanted.isEmpty()) return
        if (current.video.subtitles.none { it.language == wanted }) return
        _state.value = current.copy(subtitleLanguage = wanted)
        player.showSubtitles(wanted)
        loadCues(wanted)
    }

    private fun asState(error: Throwable): WatchState = when (error) {
        is ServerNotConfigured -> WatchState.Failed("no server")
        else -> WatchState.Failed(error.message ?: "could not play this")
    }
}
