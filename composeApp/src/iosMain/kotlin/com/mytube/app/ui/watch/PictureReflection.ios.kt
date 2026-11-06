package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.player.AvVideoPlayer
import com.mytube.app.ui.theme.Tokens
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.kCAFilterLinear
import platform.QuartzCore.kCAFilterTrilinear
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * How small each stage rasterizes, outermost first: 0.025 of the band in all,
 * which is the blur. iOS has no public filter for a layer, and
 * `UIVisualEffectView` hid the reflection outright. One stage at 0.04 showed
 * its pixels; several, each filtered on the way down and up, are smooth, so a
 * stronger blur is another stage rather than a smaller one.
 */
private val STAGES = listOf(0.25, 0.4, 0.5, 0.5)

/**
 * A second `AVPlayerLayer` on the same player, flipped, inside the stages.
 *
 * The view is opaque in the page's ground: an interop view sits behind the
 * Compose canvas, and wherever it draws nothing the hole shows white.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ReflectionView(
    private val playerLayer: AVPlayerLayer,
    private val stages: List<UIView>,
) : UIView(frame = CGRectZero.readValue()) {

    override fun layoutSubviews() {
        super.layoutSubviews()
        bounds.useContents {
            val width = size.width
            val pictureHeight = width * 9.0 / 16.0
            withoutImplicitAnimation {
                stages.forEach { it.setFrame(CGRectMake(0.0, 0.0, width, size.height)) }
                // The whole picture, flipped about its middle; the band shows
                // the top of that, which is the picture's bottom mirrored.
                playerLayer.setBounds(CGRectMake(0.0, 0.0, width, pictureHeight))
                playerLayer.setPosition(CGPointMake(width / 2, pictureHeight / 2))
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun reflectionView(playerLayer: AVPlayerLayer): UIView {
    val stages = STAGES.map { scale ->
        UIView(frame = CGRectZero.readValue()).apply {
            clipsToBounds = true
            layer.shouldRasterize = true
            layer.rasterizationScale = scale
            layer.minificationFilter = kCAFilterTrilinear
            layer.magnificationFilter = kCAFilterLinear
        }
    }
    stages.zipWithNext { outer, inner -> outer.addSubview(inner) }
    stages.last().layer.addSublayer(playerLayer)
    return ReflectionView(playerLayer, stages).apply {
        clipsToBounds = true
        userInteractionEnabled = false
        backgroundColor = UIColor.colorWithRed(
            Tokens.bg.red.toDouble(), Tokens.bg.green.toDouble(), Tokens.bg.blue.toDouble(), 1.0,
        )
        addSubview(stages.first())
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun ReflectedVideo(player: VideoPlayer, picture: LayerBackdrop?, modifier: Modifier) {
    val av = (player as? AvVideoPlayer)?.av ?: return
    val playerLayer = remember(av) {
        AVPlayerLayer().apply {
            this.player = av
            setAffineTransform(CGAffineTransformMakeScale(1.0, -1.0))
        }
    }
    DetachInBackground(playerLayer, av)
    UIKitView(
        factory = { reflectionView(playerLayer) },
        modifier = modifier,
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
    )
}
