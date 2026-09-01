package com.mytube.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mytube.app.domain.model.Playlist
import com.mytube.app.ui.home.Radius
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.GlassRadius
import com.mytube.app.ui.shell.GlassTextField
import com.mytube.app.ui.shell.ScreenTitle
import com.mytube.app.ui.shell.TabScaffold
import com.mytube.app.ui.shell.detailContentPadding
import com.mytube.app.ui.shell.glassControl
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.theme.Tokens

/**
 * The collections this member keeps.
 *
 * Reached from Settings, where it replaced the "Đã lưu" row — one row, not two:
 * the saved shelf is the first row *here*, which is where it belongs once there
 * is more than one collection.
 */
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PlaylistsContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenSaved = onOpenSaved,
        onOpenPlaylist = onOpenPlaylist,
        onRetry = viewModel::refresh,
        onStartCreating = viewModel::startCreating,
        onCancelCreating = viewModel::cancelCreating,
        onNameChanged = viewModel::nameChanged,
        onCreate = viewModel::create,
    )
}

@Composable
fun PlaylistsContent(
    state: PlaylistsState,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onRetry: () -> Unit,
    onStartCreating: () -> Unit,
    onCancelCreating: () -> Unit,
    onNameChanged: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val strings = LocalStrings.current

    Box(Modifier.fillMaxSize().glassSource()) {
        TabScaffold(
            loading = state is PlaylistsState.Loading,
            needsServer = state is PlaylistsState.NeedsServer,
            failure = (state as? PlaylistsState.Failed)?.message.orEmpty(),
            noServerTitle = strings.noServerTitle,
            setTheAddress = strings.setTheAddress,
            couldNotReach = strings.couldNotReach,
            tryAgain = strings.tryAgain,
            onOpenSettings = onOpenSettings,
            onRetry = onRetry,
        ) {
            val ready = state as? PlaylistsState.Ready ?: return@TabScaffold

            LazyColumn(Modifier.fillMaxSize(), contentPadding = detailContentPadding()) {
                item(key = "title") {
                    // The "+" is in the title row, not at the end of the list.
                    // A member with twelve collections would otherwise have to
                    // scroll past all of them to make a thirteenth.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScreenTitle(strings.playlists, Modifier.weight(1f))
                        if (!ready.creating) {
                            Icon(
                                imageVector = PlusIcon,
                                contentDescription = strings.newPlaylist,
                                tint = Tokens.text,
                                modifier = Modifier
                                    .padding(end = Space.lg)
                                    .size(Size.iconButton)
                                    .clip(GlassRadius.control)
                                    .glassControl(GlassRadius.control)
                                    .clickable(onClick = onStartCreating)
                                    .padding(Space.sm),
                            )
                        }
                    }
                }

                if (ready.creating) {
                    item(key = "new-name") {
                        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                            GlassTextField(
                                value = ready.newName,
                                onValueChange = onNameChanged,
                                placeholder = strings.playlistName,
                            )
                            Spacer(Modifier.height(Space.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                GlassButton(
                                    label = strings.createPlaylist,
                                    onClick = onCreate,
                                    primary = true,
                                    enabled = ready.newName.isNotBlank(),
                                    modifier = Modifier.padding(end = Space.xs),
                                )
                                GlassButton(strings.cancel, onCancelCreating)
                            }
                        }
                    }
                }

                // The saved shelf, first and fixed. It is not one of the rows
                // below and never arrives in that list — see `Playlist`.
                item(key = "saved") {
                    PlaylistRow(
                        title = strings.savedTitle,
                        detail = "",
                        thumbnailPath = "",
                        mediaBaseUrl = mediaBaseUrl,
                        onClick = onOpenSaved,
                        icon = BookmarkIcon,
                    )
                }

                items(ready.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        title = playlist.title,
                        detail = strings.playlistCount(playlist.itemCount),
                        thumbnailPath = playlist.thumbnailPaths.firstOrNull().orEmpty(),
                        mediaBaseUrl = mediaBaseUrl,
                        onClick = { onOpenPlaylist(playlist.id) },
                        icon = PlaylistIcon,
                    )
                }

                if (ready.playlists.isEmpty() && !ready.creating) {
                    item(key = "empty") {
                        EmptyState(strings.noPlaylists, strings.noPlaylistsDetail)
                    }
                }
            }
        }

        DetailBack(onBack, strings.back)
    }
}

/**
 * One collection, as a row.
 *
 * A wide thumbnail with the name beside it rather than a grid of cards: this is
 * a list somebody scans for one name, which is the same argument the
 * subscriptions tab settles the same way.
 */
@Composable
private fun PlaylistRow(
    title: String,
    detail: String,
    thumbnailPath: String,
    mediaBaseUrl: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(THUMBNAIL_WIDTH)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radius.thumbnail))
                .background(Tokens.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailPath.isNotEmpty()) {
                AsyncImage(
                    model = imageModel(mediaBaseUrl, thumbnailPath),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                )
            }
            // The mark sits in the corner over the picture, and alone in the
            // middle of an empty pane when a playlist has nothing in it yet —
            // an empty grey rectangle reads as a picture that failed to load.
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Tokens.text,
                modifier = Modifier
                    .then(
                        if (thumbnailPath.isEmpty()) {
                            Modifier
                        } else {
                            Modifier.align(Alignment.BottomEnd).padding(Space.xs)
                        },
                    )
                    .size(20.dp),
            )
        }

        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Tokens.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(detail, color = Tokens.text2, fontSize = 12.sp)
            }
        }
    }
}

/** The same 168dp as the up-next rail's, so the two lists read as one app. */
private val THUMBNAIL_WIDTH = 140.dp
