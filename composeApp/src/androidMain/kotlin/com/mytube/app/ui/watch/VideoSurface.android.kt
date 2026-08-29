package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.mytube.app.domain.repository.VideoPlayer
import com.mytube.app.player.ExoVideoPlayer

@UnstableApi
@Composable
actual fun VideoSurface(player: VideoPlayer, modifier: Modifier) {
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
            }
        },
        // The view outlives a recomposition, but the player it points at may
        // not: a climb to a different source replaces the object. Rebinding on
        // update is what keeps the picture attached to whatever is playing now.
        update = { it.player = controller },
        onRelease = { it.player = null },
    )
}
