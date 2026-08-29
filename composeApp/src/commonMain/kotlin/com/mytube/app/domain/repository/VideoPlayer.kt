package com.mytube.app.domain.repository

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
    fun load(url: String, startAtSeconds: Double = 0.0)

    fun play()

    fun pause()

    fun seekTo(seconds: Double)

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
) {
    val hasError: Boolean get() = error.isNotEmpty()

    /** How far through, 0..1, or 0 while the duration is unknown. */
    val progress: Float
        get() = if (durationSeconds <= 0) 0f
        else (positionSeconds / durationSeconds).coerceIn(0.0, 1.0).toFloat()
}

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
