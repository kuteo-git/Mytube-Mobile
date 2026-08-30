package com.mytube.app.ui.saved

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.ScreenTitle
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.tabContentPadding

@Composable
fun SavedScreen(
    viewModel: SavedViewModel,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SavedContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onOpenSettings = onOpenSettings,
        onOpenVideo = onOpenVideo,
        onOpenChannel = onOpenChannel,
        onRetry = viewModel::refresh,
        onUnsave = viewModel::unsave,
    )
}

@Composable
fun SavedContent(
    state: SavedState,
    mediaBaseUrl: String,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onRetry: () -> Unit,
    onUnsave: (Video) -> Unit,
) {
    val strings = LocalStrings.current

    TabScaffold(
        loading = state is SavedState.Loading,
        needsServer = state is SavedState.NeedsServer,
        failure = (state as? SavedState.Failed)?.message.orEmpty(),
        noServerTitle = strings.noServerTitle,
        setTheAddress = strings.setTheAddress,
        couldNotReach = strings.couldNotReach,
        tryAgain = strings.tryAgain,
        onOpenSettings = onOpenSettings,
        onRetry = onRetry,
    ) {
        val ready = state as? SavedState.Ready ?: return@TabScaffold

        LazyColumn(Modifier.fillMaxSize(), contentPadding = tabContentPadding()) {
            item(key = "title") { ScreenTitle(strings.savedTitle) }

            if (ready.videos.isEmpty()) {
                item(key = "empty") {
                    EmptyState(strings.noSaved, strings.noSavedDetail)
                }
            }

            items(ready.videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    mediaBaseUrl = mediaBaseUrl,
                    strings = strings,
                    onClick = { onOpenVideo(video.id) },
                    // The menu's only action here is taking it off the shelf.
                    // "Not interested" belongs to a feed, and this list is not
                    // one — every video on it was put here deliberately.
                    onSave = { onUnsave(video) },
                    onOpenChannel = { onOpenChannel(video.channel.id) },
                )
            }
        }
    }
}
