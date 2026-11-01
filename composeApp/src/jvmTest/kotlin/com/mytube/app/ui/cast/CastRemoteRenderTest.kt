package com.mytube.app.ui.cast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastDeviceKind
import com.mytube.app.domain.model.CastEnd
import com.mytube.app.domain.model.CastItem
import com.mytube.app.domain.model.CastSession
import com.mytube.app.domain.model.CastState
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.watch.WatchCast
import com.mytube.app.ui.watch.WatchContent
import com.mytube.app.ui.watch.WatchState
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * The cast's views, actually measured and drawn where they live.
 *
 * Previews never ran it: the iPhone crashed the moment the remote opened, on
 * `checkScrollableContainerConstraints` — a vertical scroll inside the
 * sheet's own vertical scroll is measured with an infinite height.
 */
class CastRemoteRenderTest {

    private val session = CastSession(
        id = "s1", device = CastDevice("r1-living", "Loa R1", CastDeviceKind.R1),
        state = CastState.Playing, end = CastEnd.Running,
        queue = List(12) { CastItem("v$it", "Song $it", "Channel", "", 200) },
        currentIndex = 0, positionSeconds = 30, durationSeconds = 200, volume = 0, version = 1,
    )

    private fun renders(content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        val scene = ImageComposeScene(width = 390 * 3, height = 844 * 3, density = Density(3f)) {
            MytubeTheme { Box(Modifier.fillMaxSize(), content = content) }
        }
        try {
            for (frame in 0..60) scene.render(frame * 16_000_000L)
        } finally {
            scene.close()
        }
    }

    @Test
    fun thePickerAndTheMiniplayerMeasureAndDraw() {
        val r1 = CastDevice("r1-living", "Loa R1", CastDeviceKind.R1)
        renders { CastPickerSheet(listOf(r1), loading = false, visible = true, backdrop = null, onPick = {}, onDismiss = {}) }
        renders { CastPickerSheet(emptyList(), loading = true, visible = true, backdrop = null, onPick = {}, onDismiss = {}) }
        renders {
            CastMiniPlayer(
                session, mediaBaseUrl = "", onExpand = {}, onPlayPause = {}, onClose = {},
                collapse = 0f, bottomInset = 0.dp,
            )
        }
    }

    /**
     * The remote is the watch screen now: the speaker's stage where the player
     * is, and its controls and queue inside the screen's own list — not a list
     * of their own inside it, which is the crash above in another form.
     */
    @Test
    fun theWatchScreenMeasuresAndDrawsAsTheRemote() {
        val cast = WatchCast(
            stage = { CastStage(session, "", {}, {}, {}, {}, {}) },
            controls = { CastControls(session, {}, {}, {}) },
            queue = { castQueueItems(session, "", {}, {}, {}, {}) },
        )
        renders {
            WatchContent(
                state = WatchState.Loading, player = null, mediaBaseUrl = "", onBack = {}, onPlayPause = {},
                onSeek = {}, onSkip = {}, onRetry = {}, onReact = {}, onToggleSaved = {}, onToggleSubscribed = {},
                onToggleNarration = {}, onToggleAutoplay = {}, onSelectSubtitles = {}, onPlayNext = {},
                onPlayPrevious = {}, hasPrevious = false, onToggleRail = {}, onFilterRail = {}, onLoadComments = {},
                onOpenVideo = {}, onOpenChannel = {}, onCast = {}, casting = true, cast = cast,
            )
        }
    }
}
