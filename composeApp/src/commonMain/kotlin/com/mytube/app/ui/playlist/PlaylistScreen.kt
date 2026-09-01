package com.mytube.app.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.MoreVertical
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.LocalBackdrop
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.GlassRadius
import com.mytube.app.ui.shell.GlassTextField
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.detailContentPadding
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.shell.menuSurface
import com.mytube.app.ui.theme.Tokens

/**
 * One collection, and what is in it.
 *
 * Opening a video from here hands the whole page's ids to the session as its
 * queue, so next and autoplay stay inside the playlist — the player needed no
 * change for that; `WatchSession.queue` already existed for the channel page.
 */
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String, List<String>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onDeleted: (playlistId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Leaving is the caller's, not this screen's: the page has no contents left
    // to draw, and which list to fall back to is a question about the route.
    // The id goes with it, so the page behind can drop the row without asking
    // the server what it already knows.
    // Same reason as the page behind it: the contents can have changed from a
    // sheet on another screen since this was last drawn.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val deleted = state as? PlaylistState.Deleted
    if (deleted != null) {
        LaunchedEffect(deleted.playlistId) { onDeleted(deleted.playlistId) }
    }

    PlaylistContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenVideo = onOpenVideo,
        onOpenChannel = onOpenChannel,
        onRetry = viewModel::refresh,
        onRemove = viewModel::remove,
        onStartRenaming = viewModel::startRenaming,
        onCancelRenaming = viewModel::cancelRenaming,
        onNameChanged = viewModel::nameChanged,
        onRename = viewModel::rename,
        onAskDelete = viewModel::askDelete,
        onCancelDelete = viewModel::cancelDelete,
        onDelete = viewModel::delete,
    )
}

@Composable
fun PlaylistContent(
    state: PlaylistState,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String, List<String>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onRetry: () -> Unit,
    onRemove: (Video) -> Unit,
    onStartRenaming: () -> Unit,
    onCancelRenaming: () -> Unit,
    onNameChanged: (String) -> Unit,
    onRename: () -> Unit,
    onAskDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current

    // Two boxes, and the nesting is load-bearing. `glassSource` registers the
    // inner one as the layer every floating pane samples — so an alert drawn
    // *inside* it samples a recording of itself, which is not a bad look but a
    // crash: `SkBlurImageFilter::onGetOutputLayerBounds` recurses until the
    // stack goes. The charter records it from the pull-to-refresh pane, and
    // this is the second time it has been paid for.
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().glassSource()) {
        TabScaffold(
            loading = state is PlaylistState.Loading,
            needsServer = state is PlaylistState.NeedsServer,
            failure = (state as? PlaylistState.Failed)?.message.orEmpty(),
            noServerTitle = strings.noServerTitle,
            setTheAddress = strings.setTheAddress,
            couldNotReach = strings.couldNotReach,
            tryAgain = strings.tryAgain,
            onOpenSettings = onOpenSettings,
            onRetry = onRetry,
        ) {
            val ready = state as? PlaylistState.Ready ?: return@TabScaffold
            val queue = ready.videos.map { it.id }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = detailContentPadding()) {
                item(key = "header") {
                    PlaylistHeader(
                        state = ready,
                        canPlay = queue.isNotEmpty(),
                        onPlayAll = { queue.firstOrNull()?.let { onOpenVideo(it, queue) } },
                        onStartRenaming = onStartRenaming,
                        onCancelRenaming = onCancelRenaming,
                        onNameChanged = onNameChanged,
                        onRename = onRename,
                        onAskDelete = onAskDelete,
                        onCancelDelete = onCancelDelete,
                        onDelete = onDelete,
                    )
                }

                if (ready.videos.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(strings.emptyPlaylist, strings.emptyPlaylistDetail)
                    }
                }

                items(ready.videos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        mediaBaseUrl = mediaBaseUrl,
                        strings = strings,
                        onClick = { onOpenVideo(video.id, queue) },
                        // The one action a row on this page has. "Not
                        // interested" belongs to a feed, and this list is not
                        // one — every video on it was put here deliberately.
                        onSave = { onRemove(video) },
                        saveLabel = strings.removeFromPlaylist,
                        onOpenChannel = { onOpenChannel(video.channel.id) },
                    )
                }
            }
        }

        DetailBack(onBack, strings.back)
        }

        // Outside the recorded node — see the comment on the boxes above.
        val ready = state as? PlaylistState.Ready

        // Prefilled with the name it has: renaming is editing a word, not
        // typing one from nothing, and an empty field asks somebody to
        // remember what they are changing.
        PlaylistNameAlert(
            visible = ready?.renaming == true,
            title = strings.renamePlaylist,
            name = ready?.newName.orEmpty(),
            backdrop = LocalBackdrop.current,
            confirmLabel = strings.savePlaylist,
            onNameChanged = onNameChanged,
            onDismiss = onCancelRenaming,
            onConfirm = onRename,
        )

        ConfirmAlert(
            visible = ready?.confirmingDelete == true,
            title = strings.deletePlaylist,
            detail = strings.deletePlaylistConfirm,
            confirmLabel = strings.deletePlaylist,
            backdrop = LocalBackdrop.current,
            onDismiss = onCancelDelete,
            onConfirm = onDelete,
        )
    }
}

@Composable
private fun PlaylistHeader(
    state: PlaylistState.Ready,
    canPlay: Boolean,
    onPlayAll: () -> Unit,
    onStartRenaming: () -> Unit,
    onCancelRenaming: () -> Unit,
    onNameChanged: (String) -> Unit,
    onRename: () -> Unit,
    onAskDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.playlist.title,
                    color = Tokens.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = strings.playlistCount(state.playlist.itemCount),
                    color = Tokens.text2,
                    fontSize = 13.sp,
                )
            }
            PlaylistMenu(onStartRenaming, onAskDelete)
        }

        Spacer(Modifier.height(Space.md))

        if (canPlay) GlassButton(strings.playAll, onPlayAll, primary = true)

        Spacer(Modifier.height(Space.md))
    }
}

/**
 * Rename and delete, on the page they act on.
 *
 * **Absent on the saved shelf**, which is a different screen entirely — the
 * pinned set has no row to rename and no row to delete, so it is not offered a
 * menu that would have to refuse.
 */
@Composable
private fun PlaylistMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }

    Box {
        Icon(
            imageVector = MoreVertical,
            contentDescription = strings.moreOptions,
            tint = Tokens.text2,
            modifier = Modifier
                .size(Size.iconButton)
                .clip(CircleShape)
                .clickable { open = true }
                .padding(Space.sm),
        )
        // Paint, not a sampled backdrop: a popup has its own coordinate space,
        // and this anchor sits inside the layer the shell records — where
        // sampling is a Skia stack overflow rather than a bad look.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color.Transparent,
            shape = GlassRadius.panel,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.menuSurface(GlassRadius.panel),
        ) {
            DropdownMenuItem(
                text = { Text(strings.renamePlaylist, color = Tokens.text) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(strings.deletePlaylist, color = Tokens.text) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}
