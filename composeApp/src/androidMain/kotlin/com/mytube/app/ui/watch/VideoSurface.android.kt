package com.mytube.app.ui.watch

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.player.ExoVideoPlayer

@UnstableApi
@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier, fill: Boolean) {
    // Null while the controller is connecting to the service. Nothing is drawn
    // until then, which is a fraction of a second and is why the watch screen
    // keeps a black box of the right shape underneath.
    val controller = (player as? ExoVideoPlayer)?.controller ?: return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.player = controller
                // The app draws its own controls, in Compose, shared with iOS.
                // Media3's are Android-only and would make the two platforms
                // look and behave differently for no gain.
                useController = false
                // And Media3 must not draw the captions either.
                //
                // `useController = false` removes the *controls* and says
                // nothing about the text: `PlayerView` keeps a `SubtitleView`
                // of its own and renders the selected track into it, so the
                // cues were drawn twice — once by Media3 here and once by this
                // app's `SubtitleOverlay`, which is Compose and shared with
                // iOS. The two disagree about where a line breaks, so what was
                // on screen was one sentence and most of the sentence before
                // it, stacked.
                //
                // Only Android had it: `AVPlayerLayer` draws no text.
                //
                // Measured mid-drag, which is what made it unmistakable rather
                // than merely doubled — Media3 sizes its text in absolute `sp`,
                // so a `PlayerView` shrunk to a third of the screen still drew
                // captions at full size, spilling out of the picture they
                // belong to and riding it down.
                //
                // The track stays selected. This app reads the cues itself, and
                // turning the track off to stop the drawing would take away
                // what `SubtitleOverlay` is drawing from.
                subtitleView?.visibility = View.GONE
                // ZOOM crops to fill; FIT letterboxes. Set on the view rather
                // than by scaling the Compose node, because the surface is a
                // real Android view and a `graphicsLayer` scale on it draws the
                // picture at the wrong size inside a correctly sized frame.
                resizeMode = if (fill) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        },
        // The view outlives a recomposition, but the player it points at may
        // not: a climb to a different source replaces the object. Rebinding on
        // update is what keeps the picture attached to whatever is playing now.
        update = {
            it.player = controller
            // Again on update, for the reason the gravity is set here on iOS:
            // a property assigned only in `factory` is one that reverts the
            // moment the view is rebuilt or the player replaced, and nothing
            // reports it.
            it.subtitleView?.visibility = View.GONE
            it.resizeMode = if (fill) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        onRelease = { it.player = null },
    )
}
