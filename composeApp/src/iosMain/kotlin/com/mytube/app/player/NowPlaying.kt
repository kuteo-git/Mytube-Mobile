package com.mytube.app.player

import com.mytube.app.domain.repository.PlayingMedia
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import platform.Foundation.numberWithBool
import platform.Foundation.numberWithDouble
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyIsLiveStream
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * What the lock screen shows, and what its buttons do.
 *
 * Two separate things on iOS, and both are needed. `MPNowPlayingInfoCenter`
 * carries the text and the scrubber; `MPRemoteCommandCenter` carries the
 * buttons. Setting only the first gives a lock screen that reads correctly and
 * whose play button does nothing.
 *
 * This is the counterpart of Android's `MediaMetadata`, and it exists for the
 * same reason that one did: measured there, a notification with no metadata read
 * "Mytube is running", which tells somebody reaching into a pocket precisely
 * nothing.
 *
 * **Untested on a device.** There is no Xcode project in this repository yet, so
 * nothing has run this — it compiles, and that is all that is claimed. Recorded
 * here rather than left to be discovered.
 */
internal class NowPlaying(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSeek: (Double) -> Unit,
) {

    /**
     * The handlers this instance put on the shared command centre.
     *
     * Kept so they can be taken off again. `MPRemoteCommandCenter` is
     * **process-wide** and its targets accumulate: a flag saying "this instance
     * has registered" stops one instance registering twice and does nothing
     * about a second instance registering beside it. Measured on the phone —
     * play a video, open another, lock the screen, press play, and *both* were
     * heard, because both players' handlers were still on the shared centre and
     * both answered the one press.
     */
    private var handlers: List<Any> = emptyList()

    /**
     * The title of the entry now on screen, so a picture that arrives late is
     * only applied if it is still the picture of what is playing.
     *
     * A thumbnail is fetched over the house wifi and a viewer can press next
     * before it lands. Without this the lock screen shows the previous video's
     * picture under the new video's title.
     */
    private var showing = ""

    /**
     * The text and the artwork.
     *
     * The picture *is* fetched, which reverses the decision recorded here
     * before: "a lock screen with a title and no picture is complete". Held up
     * against the real thing it is not — every other app's lock screen carries
     * one, and its absence reads as the entry having failed to load rather than
     * as a choice. The objections that decision was made on are answered rather
     * than ignored: the fetch is asynchronous so nothing waits for it, a
     * failure leaves the text exactly as it is, and the URL being unreachable
     * off the house wifi is true of the video itself, which is why this app is
     * wifi-only in the first place.
     */
    fun describe(media: PlayingMedia, durationSeconds: Double) {
        showing = media.title
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf(
            MPMediaItemPropertyTitle to media.title,
            MPMediaItemPropertyArtist to media.channel,
            MPMediaItemPropertyPlaybackDuration to NSNumber.numberWithDouble(durationSeconds),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber.numberWithDouble(0.0),
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber.numberWithDouble(0.0),
            // A live broadcast has no length to scrub, and saying so is what
            // stops the lock screen drawing a bar that cannot be moved.
            MPNowPlayingInfoPropertyIsLiveStream to
                NSNumber.numberWithBool(durationSeconds <= 0),
        )
        register()
        fetchArtwork(media)
    }

    /**
     * The thumbnail, applied to the entry when it arrives.
     *
     * `MPMediaItemPropertyArtwork` takes a `UIImage`, so there is no way to
     * hand iOS a URL and let it do this. The handler merges into whatever the
     * entry holds by then rather than replacing it, or a picture landing after
     * the first progress tick would take the clock back to zero.
     */
    // `UIImage.size` is a `CValue<CGSize>`, which is cinterop's experimental
    // surface — the same opt-in `AvVideoPlayer` carries for `CMTime`.
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun fetchArtwork(media: PlayingMedia) {
        if (media.artworkUrl.isEmpty()) return
        val url = NSURL.URLWithString(media.artworkUrl) ?: return
        val forTitle = media.title
        NSURLSession.sharedSession
            .dataTaskWithURL(url) { data, _, _ ->
                val image = data?.let { UIImage.imageWithData(it) } ?: return@dataTaskWithURL
                dispatch_async(dispatch_get_main_queue()) {
                    // Still the same video: a viewer can press next while this
                    // is in flight.
                    if (showing != forTitle) return@dispatch_async
                    val centre = MPNowPlayingInfoCenter.defaultCenter()
                    val existing = centre.nowPlayingInfo ?: return@dispatch_async
                    val artwork = MPMediaItemArtwork(image.size) { _ -> image }
                    centre.nowPlayingInfo =
                        existing + mapOf(MPMediaItemPropertyArtwork to artwork)
                }
            }
            .resume()
    }

    /**
     * Where the playhead is, sent on the app's own schedule.
     *
     * iOS interpolates between updates from the playback rate, so this does not
     * need to run per frame — but it does have to be sent on every *change* of
     * rate, or the lock screen's clock carries on counting through a pause.
     */
    fun progress(positionSeconds: Double, isPlaying: Boolean, durationSeconds: Double) {
        val center = MPNowPlayingInfoCenter.defaultCenter()
        val existing = center.nowPlayingInfo ?: return
        center.nowPlayingInfo = existing +
            mapOf(
                MPNowPlayingInfoPropertyElapsedPlaybackTime to
                    NSNumber.numberWithDouble(positionSeconds),
                MPNowPlayingInfoPropertyPlaybackRate to
                    NSNumber.numberWithDouble(if (isPlaying) 1.0 else 0.0),
                // The length, sent on every tick and not only at the start.
                //
                // `describe` runs the moment the item is handed to the player,
                // when the length is still zero — and a zero length is written
                // out as `IsLiveStream`, which is how iOS is told there is
                // nothing to scrub. The comment there claimed this method
                // corrected it; it did not, and the lock screen therefore had
                // no scrubber and no seek for the whole of every video.
                MPMediaItemPropertyPlaybackDuration to
                    NSNumber.numberWithDouble(durationSeconds),
                MPNowPlayingInfoPropertyIsLiveStream to
                    NSNumber.numberWithBool(durationSeconds <= 0),
            )
    }

    /** Clears the entry, so a closed video stops showing on the lock screen. */
    fun clear() {
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    /**
     * Take this player's handlers off the shared command centre.
     *
     * Separate from [clear], and called from `release()` as well as `stop()`:
     * releasing hands back this app's hold on the *buttons* even where it
     * deliberately leaves the lock-screen entry standing. A player that has let
     * go of the screen must not still answer it.
     */
    fun resign() {
        if (handlers.isEmpty()) return
        // Paired by position with what `register` added, in one list rather
        // than four fields: a fifth command added to one and not the other is
        // a handler that outlives its player, which is the fault this exists
        // to end.
        commands().zip(handlers) { command, target -> command.removeTarget(target) }
        handlers = emptyList()
    }

    private fun commands(): List<MPRemoteCommand> {
        val centre = MPRemoteCommandCenter.sharedCommandCenter()
        return listOf(
            centre.playCommand,
            centre.pauseCommand,
            centre.togglePlayPauseCommand,
            centre.changePlaybackPositionCommand,
        )
    }

    private fun register() {
        if (handlers.isNotEmpty()) return

        val (playCommand, pauseCommand, toggleCommand, seekCommand) = commands()
        val play = playCommand.addTargetWithHandler {
            onPlay()
            MPRemoteCommandHandlerStatusSuccess
        }
        val pause = pauseCommand.addTargetWithHandler {
            onPause()
            MPRemoteCommandHandlerStatusSuccess
        }
        // Handled separately from play and pause: headphone buttons and car
        // stereos send toggle rather than either, and a lock screen that works
        // while a steering wheel does not is a worse failure than neither.
        val toggle = toggleCommand.addTargetWithHandler {
            onToggle()
            MPRemoteCommandHandlerStatusSuccess
        }
        val seek = seekCommand.addTargetWithHandler { event ->
            val position = (event as? platform.MediaPlayer.MPChangePlaybackPositionCommandEvent)
                ?.positionTime
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusSuccess
            onSeek(position)
            MPRemoteCommandHandlerStatusSuccess
        }
        handlers = listOfNotNull(play, pause, toggle, seek)
    }

    private fun onToggle() {
        val rate = MPNowPlayingInfoCenter.defaultCenter()
            .nowPlayingInfo
            ?.get(MPNowPlayingInfoPropertyPlaybackRate) as? NSNumber
        if ((rate?.doubleValue ?: 0.0) > 0.0) onPause() else onPlay()
    }
}
