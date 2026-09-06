package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.player.AvVideoPlayer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * The picture, iOS side.
 *
 * ## The layer lays itself out; `onResize` is not trusted to do it
 *
 * An `AVPlayerLayer` added as a sublayer does not follow its parent's bounds —
 * left alone it keeps the zero frame it was created with and draws nothing. The
 * obvious place to fix that is `UIKitView`'s `onResize`, and that is what this
 * did. **It does not fire in this version of Compose Multiplatform**, so the
 * layer stayed at zero for the life of the screen.
 *
 * The evidence, in order, because each step ruled out the guess before it:
 *
 * | measured | ruled out |
 * |---|---|
 * | the video area was the right size and in the right place, and **white** | the container view is laid out correctly — by Compose itself, not by `onResize` |
 * | the server logged `hls tracks resolved` for the video being watched | the URL, ATS, the network, and the stream |
 * | `AVPlayerItem.status` never reached `failed` | the player and the item |
 *
 * So the container is placed, the stream is playing, and nothing is drawn: the
 * only thing left is the sublayer's own frame. `layoutSubviews` is where UIKit
 * asks a view to lay out its contents, it is called on every bounds change, and
 * it needs no cooperation from the interop API.
 *
 * `onResize` is kept below it, sizing from the *bounds* rather than the rect it
 * is handed. That rect is the frame in the **superview's** coordinates — on this
 * screen roughly `(0, 110, 390, 219)` — and giving it to a layer *inside* the
 * container offsets the picture by a further 110pt within a container 219pt
 * tall, which is its own way of drawing nothing. JetBrains' sample writes
 * exactly that and is correct only because its video fills the screen, where the
 * origin happens to be zero.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private class VideoContainer(private val playerLayer: AVPlayerLayer) :
    UIView(frame = CGRectZero.readValue()) {

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Bounds, not frame: a sublayer is positioned in its parent's own
        // coordinate space, where the origin is always zero.
        //
        // And inside a transaction with actions off, which is not a detail.
        withoutImplicitAnimation { playerLayer.setFrame(bounds) }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier, fill: Boolean) {
    val av = (player as? AvVideoPlayer)?.av ?: return

    // Remembered rather than found again with `sublayers.firstOrNull() as?
    // AVPlayerLayer`. That was a search for something this function had put
    // there itself, and a cast that silently does nothing when it fails — which
    // here means a blank picture with no error anywhere.
    val playerLayer = remember(av) {
        AVPlayerLayer().apply {
            this.player = av
            backgroundColor = UIColor.blackColor.CGColor
        }
    }

    // The gravity is set on every recomposition, not once when the layer is
    // built, and that is the whole of a real bug.
    //
    // It used to live inside the `remember` above — which runs once per player,
    // reads `fill` once, and never looks again. `fill` is `drag > 0f`, false at
    // the moment the layer is created and true for the whole of the gesture, so
    // the picture stayed letterboxed all the way down and only became a
    // centre-cropped square when the *miniplayer* composed a `VideoSurface` of
    // its own and got a fresh layer. Measured on the iPhone 16e simulator by
    // holding the drag a third of the way and screenshotting mid-gesture: a
    // full 16:9 frame inside a rounded box that was already 1.5:1.
    //
    // Android never had it. `VideoSurface.android.kt` assigns `resizeMode` in
    // both `factory` and `update`, which is what this now mirrors — the two
    // platforms were written to the same intent and only one of them kept it.
    //
    // Aspect *fill* crops to the layer's bounds; aspect fits inside it. It has
    // to be the layer's own property rather than a transform on the view: an
    // interop layer that is scaled is not a resized one.
    SideEffect {
        withoutImplicitAnimation {
        playerLayer.videoGravity = if (fill) {
            AVLayerVideoGravityResizeAspectFill
        } else {
            // A vertical video is letterboxed rather than having its sides cut
            // off. The server publishes both shapes.
            AVLayerVideoGravityResizeAspect
        }
        }
    }

    // Detach the layer while the app is in the background, and put it back on
    // the way in.
    //
    // This is what "the video pauses when the screen goes off" was. iOS stops
    // playback of a player whose video is attached to a layer that is no longer
    // on screen — the audio session being `Playback` is necessary and not
    // sufficient. With the layer let go, the same player keeps going as an
    // audio stream, which is exactly what the lock screen is for and the whole
    // reason this app exists (server charter, risk 4).
    //
    // Android needs none of this: Media3's foreground service holds playback,
    // and nothing there ties the decoder to a visible view.
    DisposableEffect(playerLayer, av) {
        val centre = NSNotificationCenter.defaultCenter
        val background = centre.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { playerLayer.player = null }
        val foreground = centre.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { playerLayer.player = av }
        onDispose {
            centre.removeObserver(background)
            centre.removeObserver(foreground)
            // Reattached on the way out, or a screen left while backgrounded
            // would leave the layer holding nothing when it is next shown.
            playerLayer.player = av
        }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            VideoContainer(playerLayer).apply {
                backgroundColor = UIColor.blackColor
                layer.addSublayer(playerLayer)
            }
        },
        onResize = { view: UIView, rect: CValue<CGRect> ->
            withoutImplicitAnimation {
                view.layer.setFrame(rect)
                rect.useContents {
                    playerLayer.setFrame(CGRectMake(0.0, 0.0, size.width, size.height))
                }
            }
        },
    )
}

/**
 * Runs a layer change with CoreAnimation's implicit animation switched off.
 *
 * # Why every geometry change here has to go through this
 *
 * A `CALayer` that is not a view's own backing layer has **implicit animations
 * on by default**: setting `frame`, `bounds` or `videoGravity` outside an
 * explicit transaction starts an animation to the new value, and the default
 * duration is a quarter of a second. UIKit turns those off for a `UIView`'s own
 * layer during layout; `playerLayer` is a sublayer this file adds by hand, so
 * nothing was turning them off for it.
 *
 * What that costs is not a nicety. The drag to the miniplayer resizes this
 * view on **every frame**, so every frame started a fresh quarter-second
 * animation toward a target that had already moved — the picture eased toward
 * the finger instead of following it, and carried on easing for a quarter of a
 * second *after the finger stopped*. Reported from the phone as the video
 * cropping "like an animation", and as it cropping on after the drag ended.
 * Compose had the geometry right the whole time; CoreAnimation was
 * interpolating it.
 *
 * The animation Compose does want — the picture travelling and shrinking — is
 * Compose's own, driven by the drag's offset. There is no second one to keep.
 */
@OptIn(ExperimentalForeignApi::class)
private inline fun withoutImplicitAnimation(block: () -> Unit) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    try {
        block()
    } finally {
        CATransaction.commit()
    }
}
