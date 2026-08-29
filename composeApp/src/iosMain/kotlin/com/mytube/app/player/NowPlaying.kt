package com.mytube.app.player

import com.mytube.app.domain.repository.PlayingMedia
import platform.Foundation.NSNumber
import platform.Foundation.numberWithBool
import platform.Foundation.numberWithDouble
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyIsLiveStream
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

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

    private var registered = false

    /**
     * The text and the artwork.
     *
     * The artwork URL is deliberately not fetched. `MPMediaItemPropertyArtwork`
     * takes a `UIImage`, so showing one means downloading and decoding it here —
     * work Coil already does for every other image in this app, on a URL that
     * is on the house wifi and unreachable the moment the phone leaves it. A
     * lock screen with a title and no picture is complete; one that blocks
     * waiting for a picture is not.
     */
    fun describe(media: PlayingMedia, durationSeconds: Double) {
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
    }

    /**
     * Where the playhead is, sent on the app's own schedule.
     *
     * iOS interpolates between updates from the playback rate, so this does not
     * need to run per frame — but it does have to be sent on every *change* of
     * rate, or the lock screen's clock carries on counting through a pause.
     */
    fun progress(positionSeconds: Double, isPlaying: Boolean) {
        val center = MPNowPlayingInfoCenter.defaultCenter()
        val existing = center.nowPlayingInfo ?: return
        center.nowPlayingInfo = existing +
            mapOf(
                MPNowPlayingInfoPropertyElapsedPlaybackTime to
                    NSNumber.numberWithDouble(positionSeconds),
                MPNowPlayingInfoPropertyPlaybackRate to
                    NSNumber.numberWithDouble(if (isPlaying) 1.0 else 0.0),
            )
    }

    /** Clears the entry, so a closed video stops showing on the lock screen. */
    fun clear() {
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    private fun register() {
        if (registered) return
        registered = true

        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.playCommand.addTargetWithHandler {
            onPlay()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.pauseCommand.addTargetWithHandler {
            onPause()
            MPRemoteCommandHandlerStatusSuccess
        }
        // Handled separately from play and pause: headphone buttons and car
        // stereos send toggle rather than either, and a lock screen that works
        // while a steering wheel does not is a worse failure than neither.
        commands.togglePlayPauseCommand.addTargetWithHandler {
            onToggle()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val position = (event as? platform.MediaPlayer.MPChangePlaybackPositionCommandEvent)
                ?.positionTime
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusSuccess
            onSeek(position)
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun onToggle() {
        val rate = MPNowPlayingInfoCenter.defaultCenter()
            .nowPlayingInfo
            ?.get(MPNowPlayingInfoPropertyPlaybackRate) as? NSNumber
        if ((rate?.doubleValue ?: 0.0) > 0.0) onPause() else onPlay()
    }
}
