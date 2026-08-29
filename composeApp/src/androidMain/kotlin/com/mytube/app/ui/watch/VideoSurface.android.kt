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
    val exo = (player as? ExoVideoPlayer)?.exo ?: return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.player = exo
                // The app draws its own controls, in Compose, shared with iOS.
                // Media3's are Android-only and would make the two platforms
                // look and behave differently for no gain.
                useController = false
            }
        },
        // The view outlives a recomposition, but the player it points at may
        // not: a climb to a different source replaces the object. Rebinding on
        // update is what keeps the picture attached to whatever is playing now.
        update = { it.player = exo },
        onRelease = { it.player = null },
    )
}
