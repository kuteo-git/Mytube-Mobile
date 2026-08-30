package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
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
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
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
        playerLayer.setFrame(bounds)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    val av = (player as? AvVideoPlayer)?.av ?: return

    // Remembered rather than found again with `sublayers.firstOrNull() as?
    // AVPlayerLayer`. That was a search for something this function had put
    // there itself, and a cast that silently does nothing when it fails — which
    // here means a blank picture with no error anywhere.
    val playerLayer = remember(av) {
        AVPlayerLayer().apply {
            this.player = av
            // Aspect, not aspect-fill: a 16:9 video in a 16:9 box is unaffected,
            // and a vertical one is letterboxed rather than having its sides cut
            // off. The server publishes both.
            videoGravity = AVLayerVideoGravityResizeAspect
            backgroundColor = UIColor.blackColor.CGColor
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
            view.layer.setFrame(rect)
            rect.useContents {
                playerLayer.setFrame(CGRectMake(0.0, 0.0, size.width, size.height))
            }
        },
    )
}
