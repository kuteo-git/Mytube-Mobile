package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.domain.repository.VideoPlayer

/**
 * The picture itself.
 *
 * ## Why this one is `expect/actual` when the player is not
 *
 * The player is a port: an interface the domain declares and each platform
 * implements, injected at the composition root. That works because a player is
 * an object with methods.
 *
 * A view is not. Compose Multiplatform has no cross-platform way to host a
 * `PlayerView` or an `AVPlayerLayer`, and the composable that does it is
 * different in shape, not merely in implementation — `AndroidView` on one side,
 * `UIKitView` on the other. There is nothing to declare an interface *for*, so
 * `expect/actual` is the honest mechanism rather than a wrapper pretending
 * otherwise.
 *
 * It takes the port and casts inside each actual. That cast is the seam's one
 * ugly edge, and it is contained: the only way to reach it is to hand this
 * function a player built by a different platform's factory, which the
 * composition root makes impossible.
 */
@Composable
expect fun VideoSurface(
    player: VideoPlayer,
    modifier: Modifier,
    /**
     * Whether the picture fills its box, cropping what does not fit.
     *
     * False everywhere the video is the thing being watched: a letterbox is
     * honest, and cropping a film to a phone's shape throws away the edges of
     * every shot. True in exactly one place — the miniplayer's round window,
     * where a 16:9 picture fitted inside a circle is a stripe with two black
     * caps, and where nobody is watching the edges anyway.
     */
    fill: Boolean = false,
)

/**
 * What records the picture for the controls over it to sample
 * (`LocalPictureBackdrop`), or null where Compose cannot see the picture.
 *
 * Android's video is a TextureView, which the window draws, so a layer recorded
 * around [VideoSurface] has the frame in it. iOS's is an `AVPlayerLayer` that
 * Compose never draws — there iOS 26 has [com.mytube.app.ui.shell.GlassPane]
 * and earlier versions keep the paint — and a recording would also nest inside
 * the watch page's own, which Skia does not survive.
 */
@Composable
expect fun rememberPictureBackdrop(): LayerBackdrop?
