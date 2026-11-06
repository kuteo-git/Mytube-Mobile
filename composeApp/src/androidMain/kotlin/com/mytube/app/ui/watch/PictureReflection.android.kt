package com.mytube.app.ui.watch

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.ui.theme.Tokens

/**
 * The recording the controls over the picture already sample, drawn again
 * upside down: a second `VideoSurface` would steal the player's surface.
 */
@Composable
internal actual fun ReflectedVideo(player: VideoPlayer, picture: LayerBackdrop?, modifier: Modifier) {
    val recorded = picture?.graphicsLayer ?: return
    Spacer(
        modifier
            .graphicsLayer {
                val radius = REFLECTION_BLUR.toPx()
                renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                clip = true
            }
            .drawBehind {
                // The ground the clamped edges of the blur spread.
                drawRect(Tokens.bg)
                // From the band's width, not the layer's size, which is not
                // snapshot state and would not redraw this when it changed.
                val pictureHeight = size.width * 9f / 16f
                withTransform({ scale(1f, -1f, pivot = Offset(0f, pictureHeight / 2f)) }) {
                    drawLayer(recorded)
                }
            },
    )
}
