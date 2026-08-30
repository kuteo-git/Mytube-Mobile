package com.mytube.app.domain.repository

import com.mytube.app.domain.model.NarrationClip
import kotlinx.coroutines.flow.StateFlow

/**
 * Playing a video, whatever is underneath.
 *
 * ## Why a port and not `expect/actual`
 *
 * `expect/actual` would work, and it would be a second seam doing the job this
 * interface already does. A port declared by the layer that needs it, with an
 * implementation supplied per platform, is the same mechanism as
 * `SettingsDataSource` — so there is one pattern in this codebase for "the
 * platform provides this", not two.
 *
 * It also buys something `expect/actual` cannot: a fake. A ViewModel written
 * against this can be driven through buffering, failure and the end of a video
 * in a unit test, none of which is reachable on a device without a network that
 * misbehaves on cue.
 *
 * The **view** is a different matter and does use `expect/actual`: a
 * `@Composable` that wraps `PlayerView` or `AVPlayerLayer` has no
 * platform-neutral shape to hide behind. See `VideoSurface`.
 *
 * ## Why seconds are Doubles
 *
 * Both platforms report in fractions of a second, and rounding to Int at the
 * boundary would make a progress bar step. The domain's `Seconds` value class is
 * not used here because a player legitimately reports a negative or unknown
 * position before it has loaded, and that type refuses negatives by design.
 */
interface VideoPlayer {

    val state: StateFlow<PlaybackState>

    /**
     * Point the player at a stream and start buffering.
     *
     * `startAtSeconds` is where the viewer left off. Passed at load rather than
     * seeked to afterwards, because a seek issued before the media is ready is
     * either ignored or, on some players, obeyed twice.
     */
    fun load(media: PlayingMedia, startAtSeconds: Double = 0.0)

    fun play()

    fun pause()

    fun seekTo(seconds: Double)

    /**
     * Speak these lines over the video, ducking it while a line runs.
     *
     * ## Why this is on the player and not a second port
     *
     * Only the player knows where the playhead is, and only the player can turn
     * the video down. A separate `Narrator` would need both — so it would need
     * the player, and the seam would exist to be threaded through rather than to
     * separate anything.
     *
     * An empty list switches narration off, which is deliberately the same call:
     * "narrate nothing" and "stop narrating" are one state, and two methods for
     * it would be two states that can disagree.
     *
     * The list is replaced, not appended to. The server's pass grows while it
     * runs, so this is called repeatedly with a longer list each time; anything
     * already speaking keeps speaking.
     */
    fun narrate(clips: List<NarrationClip>)

    /**
     * How loud the voice is, and how far the video ducks under it.
     *
     * On the player for the same reason [narrate] is: only the player can turn
     * the video down, and the two levels are meaningless apart. Both are
     * fractions of the video's own level — the voice may exceed 1, because
     * synthesised speech is quieter than film audio.
     *
     * Safe to call before there is anything to narrate, and before the platform
     * has finished connecting: the level is remembered and applied when a voice
     * exists. Without that the levels read from the device at startup would be
     * dropped on the floor for the first video of every launch.
     */
    fun setNarrationLevels(voice: Float, duck: Float)

    /**
     * Show one of the tracks handed over at load, or none.
     *
     * An empty language switches subtitles off. It selects rather than loads:
     * everything was attached at `load`, so this is a track-selection change and
     * costs no buffering.
     */
    fun showSubtitles(language: String)

    /**
     * Whether this player puts the captions on the picture itself.
     *
     * True on Android, where ExoPlayer is handed the `.vtt` files at load and
     * draws the cues. False on iOS: AVPlayer will not take a caption file that
     * is not in the HLS manifest, and this server's manifest carries none — so
     * the watch screen parses the file and draws the words over the video.
     *
     * A property on the player rather than a platform check in the UI, because
     * it is a fact about *this player*. Asking the object that knows is what
     * stops both from drawing at once, and two sets of captions on one picture
     * is a worse failure than none.
     */
    val rendersSubtitles: Boolean

    /**
     * Stop playing and let go of the media, ending the session.
     *
     * Distinct from [release], and the distinction is the whole of what a
     * miniplayer means. `release` hands back this app's *connection* while the
     * sound carries on — that is what leaving the watch screen does. This ends
     * the playback itself, and it is what the miniplayer's close button and the
     * lock-screen notification mean.
     *
     * Measured on the emulator before it existed: pressing the close button
     * removed the bar and left the video playing with nothing on screen at all,
     * which is worse than the fault the miniplayer was written to fix.
     */
    fun stop()

    /**
     * Give up the decoder and the audio focus.
     *
     * Not optional, and not something a garbage collector will do: both
     * platforms hold system resources that are scarce — one hardware decoder,
     * one audio session — and leaking them means the *next* video will not play.
     */
    fun release()
}

/**
 * What the player is doing.
 *
 * One object rather than several flows, because a screen reads them together and
 * separate streams would let it draw a position from one frame beside a duration
 * from another.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    /**
     * Waiting for bytes.
     *
     * Separate from `!isPlaying`: a video that is buffering is *trying* to play,
     * and the difference is a spinner over the picture rather than a play button
     * in the middle of it.
     */
    val isBuffering: Boolean = false,
    val positionSeconds: Double = 0.0,
    /** Zero until the player knows, which is a moment after loading. */
    val durationSeconds: Double = 0.0,
    /**
     * Empty unless playback failed.
     *
     * A message rather than an exception: nothing above this catches it, and the
     * only thing to do with it is show it. Non-null by the project's rule —
     * empty means fine.
     */
    val error: String = "",
    /**
     * The video reached its end.
     *
     * Its own field rather than `position >= duration`: those are both zero
     * before anything loads, and a broadcast has no duration at all, so the
     * comparison says "finished" for every live video the moment it opens.
     * Only the player knows, and both platforms are told by their own APIs.
     */
    val hasEnded: Boolean = false,
) {
    val hasError: Boolean get() = error.isNotEmpty()

    /** How far through, 0..1, or 0 while the duration is unknown. */
    val progress: Float
        get() = if (durationSeconds <= 0) 0f
        else (positionSeconds / durationSeconds).coerceIn(0.0, 1.0).toFloat()
}

/**
 * What a stream is, as far as the system outside this app is concerned.
 *
 * The URL alone is not enough, and the gap is visible: without a title the
 * lock-screen notification says "Mytube is running", which tells somebody
 * reaching for their phone in a pocket precisely nothing. The operating system
 * draws these three, so the player has to carry them.
 */
data class PlayingMedia(
    val url: String,
    val title: String,
    val channel: String,
    /** Absolute; empty when there is none. */
    val artworkUrl: String,
    /**
     * The caption tracks to side-load, already absolute.
     *
     * Handed over at load rather than added afterwards, because both platforms
     * attach a text track to the *media item* — adding one later means building
     * a new item and re-preparing, which restarts the video.
     */
    val subtitles: List<PlayingSubtitle> = emptyList(),
)

data class PlayingSubtitle(val url: String, val language: String, val label: String)

/**
 * Builds a player.
 *
 * A factory rather than a single shared instance: the watch screen owns one for
 * as long as it is open and releases it on the way out, and handing the same
 * object to two screens would mean two owners of one decoder.
 */
interface VideoPlayerFactory {
    fun create(): VideoPlayer
}
