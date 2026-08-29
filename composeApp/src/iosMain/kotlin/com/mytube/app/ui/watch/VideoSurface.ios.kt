package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.player.AvVideoPlayer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayerLayer
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
    val av = (player as? AvVideoPlayer)?.av ?: return

    UIKitView(
        modifier = modifier,
        factory = {
            val view = UIView()
            val layer = AVPlayerLayer()
            layer.player = av
            // Aspect, not aspect-fill: a 16:9 video in a 16:9 box is unaffected,
            // and a vertical one is letterboxed rather than having its sides cut
            // off. The server publishes both.
            layer.videoGravity = AVLayerVideoGravityResizeAspect
            view.layer.addSublayer(layer)
            view
        },
        // An AVPlayerLayer does not follow its parent's bounds on its own, so
        // without this the picture stays at the size the view had when it was
        // created — zero — and nothing is ever drawn.
        onResize = { view, rect ->
            view.layer.setFrame(rect)
            (view.layer.sublayers?.firstOrNull() as? AVPlayerLayer)?.setFrame(rect)
        },
    )
}
