package com.mytube.app.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.domain.model.StoryboardState
import com.mytube.app.domain.repository.VideoPlayer

/** The band's height as a fraction of the picture's. */
internal const val REFLECTION_BAND = 0.45f

/** How strong the reflection is at its top edge; it fades to black at the bottom. 0.85, 0.6 and 0.45 were too bright. */
internal const val REFLECTION_PEAK = 0.4f

/** The blur on Android and on the scrub still. iOS blurs by rasterizing small (`PictureReflection.ios.kt`). */
internal val REFLECTION_BLUR = 32.dp

/**
 * Whether the band under the picture reflects it. [effects] is the device's
 * switch: unblurred, the mirrored edges and captions under the title fight the
 * reflection for legibility, so where the platform cannot blur there is none.
 *
 * Gone from the first pixel of the drag to the miniplayer, on both platforms:
 * on iOS the band is a `UIKitView`, which Compose's alpha does not reach and
 * which sits over the shrinking picture's own interop view, so it is disposed
 * rather than faded. The page under it is gone by a sixth of the journey anyway
 * (`CONTENT_FADE_RATE`).
 *
 * Kept through the pull and in upright fullscreen, where the picture keeps its
 * 16:9 box and the band travels under it; only sideways is there no room.
 */
internal fun showsReflection(
    effects: Boolean,
    playing: Boolean,
    casting: Boolean,
    fullscreen: Fullscreen,
    drag: Float,
): Boolean = effects && playing && !casting && fullscreen != Fullscreen.Landscape && drag == 0f

/**
 * The picture mirrored in the band under it, dark and blurred, live.
 *
 * While a finger is on the seek bar the still being aimed at is reflected
 * instead, as the picture shows it.
 */
@Composable
internal fun PictureReflection(
    player: VideoPlayer,
    /** The Android recording of the picture; null on iOS, which reflects the player itself. */
    picture: LayerBackdrop?,
    /** Where the finger is on the seek bar, or -1. Read here, so a scrub recomposes only the band. */
    scrub: FloatState,
    storyboard: StoryboardState,
    durationSeconds: Double,
    mediaBaseUrl: String,
    modifier: Modifier,
) {
    Box(modifier.clipToBounds()) {
        ReflectedVideo(player, picture, Modifier.matchParentSize())
        val aimedAt = scrub.floatValue
        if (aimedAt >= 0f) {
            // The whole 16:9 still, overflowing the band and flipped about its
            // own middle, so the band holds its bottom edge mirrored as it does
            // the video's. Opaque, so it covers the live reflection under it.
            ScrubPreview(
                storyboard = storyboard,
                seconds = aimedAt * durationSeconds,
                mediaBaseUrl = mediaBaseUrl,
                fill = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .aspectRatio(16f / 9f)
                    .graphicsLayer { scaleY = -1f }
                    .blur(REFLECTION_BLUR, BlurredEdgeTreatment.Rectangle),
            )
        }
        // In Compose rather than as a mask on the native view: on iOS a
        // `CAGradientLayer` mask came out empty, and Compose draws over an
        // interop view's hole.
        Spacer(
            Modifier
                .matchParentSize()
                .background(
                    // Into the watch page's ground, which is black on the page
                    // and in fullscreen: into anything else, the band ends in
                    // an edge.
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - REFLECTION_PEAK), Color.Black)),
                ),
        )
    }
}

/** The live picture, flipped so the band's top edge meets the picture's bottom edge. */
@Composable
internal expect fun ReflectedVideo(player: VideoPlayer, picture: LayerBackdrop?, modifier: Modifier)
