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
    /**
     * A broadcast, which is measured by a window rather than by a length.
     *
     * On the port rather than derived from the two fields below, because it is
     * true from the moment the item is handed over and the window arrives a
     * moment later — deriving it would call a broadcast recorded for as long as
     * that takes, which is exactly when the bar is first drawn.
     */
    val isLive: Boolean = false,
    /**
     * The rewindable window, in the player's own timebase. Both zero until it
     * is known, and `hasLiveWindow` names that absence rather than implying it.
     *
     * A broadcast declares no duration — `AVPlayerItem.duration` is NaN and
     * ExoPlayer answers `TIME_UNSET` — so this is the only honest statement of
     * length available for one. Measured through this server on two real
     * broadcasts: 0..3605 and 0..1285.
     */
    val liveStartSeconds: Double = 0.0,
    val liveEndSeconds: Double = 0.0,
) {
    val hasError: Boolean get() = error.isNotEmpty()

    /** Set once the player has reported a window with something in it. */
    val hasLiveWindow: Boolean get() = isLive && liveEndSeconds > liveStartSeconds

    /**
     * How far through, 0..1, or 0 while there is nothing to measure against.
     *
     * A broadcast is measured **from the window's start, not from zero**: the
     * window slides forward, and drawing from zero gives a bar whose filled
     * part shrinks while the picture advances.
     */
    val progress: Float
        get() = when {
            hasLiveWindow -> {
                val span = liveEndSeconds - liveStartSeconds
                ((positionSeconds - liveStartSeconds) / span).coerceIn(0.0, 1.0).toFloat()
            }
            durationSeconds <= 0 -> 0f
            else -> (positionSeconds / durationSeconds).coerceIn(0.0, 1.0).toFloat()
        }

    /**
     * Watching what is happening, rather than a rewind.
     *
     * Not exact equality: the edge moves while the picture plays, so a viewer
     * who has touched nothing sits a segment or two behind it permanently. Ten
     * seconds is about two segments at the 5s target duration these playlists
     * declare — the web app's own number, measured there.
     */
    val atLiveEdge: Boolean
        get() = hasLiveWindow && liveEndSeconds - positionSeconds < LIVE_EDGE_TOLERANCE

    /**
     * Where a fraction of the bar lands, in the player's own timebase.
     *
     * The clamp at the far end is not tidiness. Measured on the iPhone 16e
     * simulator against a broadcast with an hour of rewind: a seek to 99% of
     * the window arrived (−2:23), and a seek to **exactly** `liveEndSeconds`
     * did nothing at all — AVPlayer ignores a target sitting on the end of its
     * own seekable range. So dragging the bar fully right, and pressing the
     * LIVE pill, both silently did nothing while every other position worked.
     *
     * Half the edge tolerance back: far enough inside the range for the seek to
     * be taken, and near enough that `atLiveEdge` still calls it live — the two
     * numbers have to be related or the pill would seek somewhere it then
     * refuses to call the edge.
     */
    fun seekTarget(fraction: Double): Double = when {
        hasLiveWindow -> {
            val span = liveEndSeconds - liveStartSeconds
            (liveStartSeconds + fraction * span).coerceAtMost(liveEdgeTarget)
        }
        else -> fraction * durationSeconds
    }

    /** Where "go back to live" lands. @see seekTarget */
    val liveEdgeTarget: Double get() = liveEndSeconds - LIVE_EDGE_TOLERANCE / 2

    /**
     * Where a jump of `bySeconds` lands, in the player's own timebase.
     *
     * **A broadcast has no duration, and clamping to one sent every jump to
     * zero.** The clamp used to be `coerceIn(0.0, max(durationSeconds - 1, 0))`
     * — right for a file, and for a stream that reports `durationSeconds` 0 it
     * is `coerceIn(0.0, 0.0)`, a constant. Measured on the iPhone 16e
     * simulator: double-tapping either half of a live picture went to the start
     * of the rewind window, an hour back, in both directions.
     *
     * So the bound is the window when there is one. Forward off the end lands
     * on `liveEdgeTarget` rather than short of it, which is the same place the
     * LIVE pill goes — one target for "as live as this player will accept",
     * because two would eventually disagree.
     *
     * A recorded video keeps the clamp a second short of its end, and the
     * reason is unchanged: seeking past the end on a media playlist leaves some
     * players buffering toward a position that will never arrive, which looks
     * exactly like a stream that has died.
     */
    fun skipTarget(bySeconds: Double): Double {
        val target = positionSeconds + bySeconds
        return if (hasLiveWindow) {
            target.coerceIn(liveStartSeconds, liveEdgeTarget)
        } else {
            target.coerceIn(0.0, maxOf(durationSeconds - 1, 0.0))
        }
    }
}

/** @see PlaybackState.atLiveEdge */
const val LIVE_EDGE_TOLERANCE = 10.0

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
    /**
     * A broadcast.
     *
     * Passed in rather than inferred from the URL or from a NaN duration: the
     * caller has already been told by the server, and inferring it would mean
     * the player calls every video live for the moment before its metadata
     * arrives — which is precisely when the bar is first drawn.
     */
    val isLive: Boolean = false,
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
