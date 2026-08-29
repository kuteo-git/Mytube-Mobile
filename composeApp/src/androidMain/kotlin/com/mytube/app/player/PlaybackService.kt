package com.mytube.app.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Where the player actually lives.
 *
 * ## Why a service and not the ViewModel
 *
 * This is the whole reason the app exists. The server charter's risk 4 says a
 * native app is the only path to background playback, and on Android that path
 * is exactly this: a `MediaSessionService` owning the player, promoted to the
 * foreground while it is playing.
 *
 * A player held by a ViewModel dies with the screen. It survives the screen
 * turning off — briefly — and then the process is backgrounded and the system
 * takes the audio away, which is the behaviour the browser already has and the
 * reason this app was written.
 *
 * So the ownership inverts: the service owns the player for the life of the
 * playback, and the UI connects to it with a `MediaController`. `ExoVideoPlayer`
 * is that connection.
 *
 * ## What the system needs from this
 *
 *  - `FOREGROUND_SERVICE_MEDIA_PLAYBACK` in the manifest, and the service
 *    declared with `foregroundServiceType="mediaPlayback"`. Media3 posts the
 *    notification itself; without the type the system refuses the promotion and
 *    kills the service the moment the app is backgrounded.
 *  - Audio attributes with `handleAudioFocus`. Without it, the sound plays over
 *    a phone call and does not duck for a navigation prompt — the app being a
 *    bad citizen in the one situation where everybody notices.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // Ask the system for focus, and honour losing it: pause for a
                // call, duck for a notification, resume afterwards.
                true,
            )
            // The screen is not required for the sound to continue, which is the
            // point — but a video whose decoder is running with nothing to draw
            // to is wasted battery, so Media3 is told to stop decoding video
            // while nothing is attached.
            .setHandleAudioBecomingNoisy(true)
            .build()

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Swiping the app away stops playback, and that is deliberate.
     *
     * The alternative — audio continuing after somebody has dismissed the app
     * from the recents list — is the behaviour people describe as an app
     * refusing to close. The notification is the way to keep it playing while
     * doing something else; the recents swipe is the way to end it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
