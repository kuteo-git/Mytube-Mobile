package com.mytube.app.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.mytube.app.domain.repository.VideoPlayer

@Composable
internal actual fun ReflectedVideo(player: VideoPlayer, picture: LayerBackdrop?, modifier: Modifier) = Unit
