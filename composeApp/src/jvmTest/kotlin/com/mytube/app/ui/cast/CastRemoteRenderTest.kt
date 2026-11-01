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
import kotlin.test.Test

/**
 * The remote, actually measured and drawn, inside the sheet it lives in.
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
    fun thePickerAndTheBarMeasureAndDraw() {
        val r1 = CastDevice("r1-living", "Loa R1", CastDeviceKind.R1)
        renders { CastPickerSheet(listOf(r1), loading = false, visible = true, backdrop = null, onPick = {}, onDismiss = {}) }
        renders { CastPickerSheet(emptyList(), loading = true, visible = true, backdrop = null, onPick = {}, onDismiss = {}) }
        renders { CastBar(session, mediaBaseUrl = "", onOpen = {}) }
    }

    @Test
    fun theRemoteMeasuresAndDrawsInItsSheet() {
        val scene = ImageComposeScene(width = 390 * 3, height = 844 * 3, density = Density(3f)) {
            MytubeTheme {
                Box(Modifier.fillMaxSize()) {
                    CastRemoteSheet(
                        session = session, visible = true, backdrop = null, mediaBaseUrl = "",
                        onDismiss = {}, onPlayPause = {}, onNext = {}, onStop = {}, onSeek = {},
                        onVolume = {}, onHandoff = {}, onJump = {}, onMoveUp = {}, onMoveDown = {},
                        onRemove = {},
                    )
                }
            }
        }
        try {
            // Several frames: the sheet rises over a few, and its content is
            // measured once it is shown.
            for (frame in 0..60) scene.render(frame * 16_000_000L)
        } finally {
            scene.close()
        }
    }
}
