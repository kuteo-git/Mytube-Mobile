package com.mytube.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.mytube.app.ui.home.imageModel
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.settings.TickIcon
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.GlassRadius
import com.mytube.app.ui.shell.GlassSheet
import com.mytube.app.ui.shell.GlassTextField
import com.mytube.app.ui.shell.glassControl
import com.mytube.app.ui.theme.Tokens

/**
 * Which collections this video belongs in.
 *
 * The bookmark used to write one bit — keep this file when the disk fills — and
 * there was nowhere to say *which* collection, so the household could not keep
 * music apart from news. Pressing it now opens this.
 *
 * ## The first row is not a playlist
 *
 * It is the saved shelf: the pinned set, which is not a row in the server's
 * `playlists` table. That is *why* it cannot be renamed or deleted, and it is
 * why it is drawn here rather than arriving in the list — see [SaveTarget] and
 * `domain/model/Playlist.kt`.
 *
 * ## Why it is a [GlassSheet] and not a `ModalBottomSheet`
 *
 * The reasons are all in `GlassSheet`'s own comment; the one that matters at
 * this call site is that it is an ordinary child of the caller's full-screen
 * `Box` and must therefore be **written last**, or it opens underneath the page
 * it belongs to.
 */
@Composable
fun BoxScope.SavePlaylistSheet(
    viewModel: SavePlaylistViewModel,
    visible: Boolean,
    mediaBaseUrl: String,
    backdrop: LayerBackdrop?,
    onDismiss: () -> Unit,
    onSaved: (saved: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SavePlaylistSheetContent(
        state = state,
        visible = visible,
        mediaBaseUrl = mediaBaseUrl,
        backdrop = backdrop,
        onDismiss = onDismiss,
        onToggleSaved = viewModel::toggleSaved,
        onToggle = viewModel::toggle,
        onStartCreating = viewModel::startCreating,
        onCancelCreating = viewModel::cancelCreating,
        onNameChanged = viewModel::nameChanged,
        onCreate = viewModel::create,
        onRetry = viewModel::retry,
        hasChanges = viewModel::hasChanges,
        onSave = { viewModel.save(onSaved) },
    )
}

@Composable
fun BoxScope.SavePlaylistSheetContent(
    state: SaveSheetState,
    visible: Boolean,
    mediaBaseUrl: String = "",
    backdrop: LayerBackdrop?,
    onDismiss: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggle: (String) -> Unit,
    onStartCreating: () -> Unit,
    onCancelCreating: () -> Unit,
    onNameChanged: (String) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    hasChanges: (SaveSheetState.Ready) -> Boolean,
    onSave: () -> Unit,
) {
    val strings = LocalStrings.current

    val creating = (state as? SaveSheetState.Ready)?.creating == true

    // Dimmed, unlike the player's settings sheet: that one sits over the video
    // it adjusts, and this one covers a feed it has no relationship with.
    //
    // **The sheet stands down while the alert is up.** Two panes of glass over
    // each other, one asking which lists and one asking for a name, is two
    // questions on screen at once — and the lower one is not answerable while
    // the upper one is.
    GlassSheet(
        visible = visible && !creating,
        backdrop = backdrop,
        scrim = SHEET_SCRIM,
        onDismiss = onDismiss,
        maxHeightFraction = SHEET_FRACTION,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // No navigation inset here any more: the sheet floats clear
                // of the bottom edge and clears the home indicator itself, so
                // adding it again would be the gap counted twice.
                .padding(start = Space.lg, end = Space.lg, bottom = Space.lg),
        ) {
            // The title, and the one control that is not a row: making a new
            // playlist. Top right, as on every sheet that offers to add
            // something to the list it is showing.
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.saveToPlaylist,
                    color = Tokens.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state is SaveSheetState.Ready) {
                    Icon(
                        imageVector = PlusIcon,
                        contentDescription = strings.newPlaylist,
                        tint = Tokens.text,
                        modifier = Modifier
                            .size(Size.iconButton)
                            .clip(GlassRadius.control)
                            .glassControl(GlassRadius.control)
                            .clickable(onClick = onStartCreating)
                            .padding(Space.sm),
                    )
                }
            }

            when (state) {
                is SaveSheetState.Loading -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Tokens.text2, strokeWidth = 2.dp)
                }

                is SaveSheetState.Failed -> Column(Modifier.padding(vertical = Space.lg)) {
                    Text(strings.couldNotReach, color = Tokens.text2, fontSize = 14.sp)
                    Spacer(Modifier.height(Space.md))
                    GlassButton(strings.tryAgain, onRetry)
                }

                is SaveSheetState.Ready -> {
                    // The rows scroll, and the sheet's own drag handle is above
                    // them: a scrolling container placed over the handle takes
                    // the gesture that dismisses.
                    LazyColumn(
                        Modifier.heightIn(max = LIST_MAX_HEIGHT),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        item(key = "saved") {
                            TickRow(
                                label = strings.savedTitle,
                                ticked = state.savedTicked,
                                onClick = onToggleSaved,
                                icon = BookmarkIcon,
                            )
                        }

                        items(state.playlists, key = { it.id }) { playlist ->
                            TickRow(
                                label = playlist.title,
                                detail = strings.playlistCount(playlist.itemCount),
                                ticked = playlist.id in state.ticked,
                                onClick = { onToggle(playlist.id) },
                                thumbnailPath = playlist.thumbnailPaths.firstOrNull().orEmpty(),
                                mediaBaseUrl = mediaBaseUrl,
                            )
                        }

                    }

                    Spacer(Modifier.height(Space.lg))
                    GlassButton(
                        label = strings.savePlaylist,
                        onClick = onSave,
                        primary = true,
                        // Nothing to apply means nothing to press. A Save that
                        // sends no requests and closes the sheet looks the same
                        // as one that worked, which is how a control that does
                        // nothing goes unnoticed.
                        enabled = hasChanges(state),
                        loading = state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    CreateAlert(state, backdrop, onNameChanged, onCancelCreating, onCreate)
}

/**
 * One collection, ticked or not.
 *
 * **Selected is a change of kind, not of shade.** `glassControl(selected = true)`
 * swaps to the app's inverted surface — solid and light — and the content colour
 * follows it. That is the lesson the Like button cost twice: `surface` and
 * `surfaceHover` are six units apart, which is invisible as a state, and a white
 * label left on a white selected pane is invisible outright.
 */
@Composable
private fun TickRow(
    label: String,
    ticked: Boolean,
    onClick: () -> Unit,
    detail: String = "",
    /** The playlist's first thumbnail, or empty for a row drawn with an icon. */
    thumbnailPath: String = "",
    mediaBaseUrl: String = "",
    icon: ImageVector? = null,
) {
    val content = if (ticked) Tokens.invertText else Tokens.text
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .glassControl(GlassRadius.control, selected = ticked)
            .clickable(onClick = onClick)
            .padding(start = Space.sm, end = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Round and cropped from the centre, like the miniplayer's window and
        // for its reason: a 16:9 frame fitted inside a circle is a stripe with
        // two blank caps, which reads as a broken image.
        Box(
            Modifier
                .size(THUMBNAIL)
                .clip(CircleShape)
                .background(if (ticked) Tokens.invertText.copy(alpha = 0.12f) else Tokens.surface),
            contentAlignment = Alignment.Center,
        ) {
            when {
                thumbnailPath.isNotEmpty() -> AsyncImage(
                    model = imageModel(mediaBaseUrl, thumbnailPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // The shelf has no first video to show, and an empty circle is
                // a picture that failed to load. Its own mark instead.
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
                // A playlist with nothing in it yet: the collection's own mark,
                // for the same reason.
                else -> Icon(
                    imageVector = PlaylistIcon,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.size(Space.md))

        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = content,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    color = if (ticked) Tokens.invertText.copy(alpha = 0.7f) else Tokens.text2,
                    fontSize = 12.sp,
                )
            }
        }
        if (ticked) {
            Icon(
                imageVector = TickIcon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Naming a new list, over the sheet that asked which list.
 *
 * Written **after** the sheet, because both are children of the caller's Box and
 * a child is on top only if it is written last. The rule `ProfileSheet` cost the
 * app once, and this is the second thing in the app that depends on it.
 */
@Composable
private fun BoxScope.CreateAlert(
    state: SaveSheetState,
    backdrop: LayerBackdrop?,
    onNameChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    val strings = LocalStrings.current
    val ready = state as? SaveSheetState.Ready

    PlaylistNameAlert(
        visible = ready?.creating == true,
        title = strings.newPlaylist,
        name = ready?.newName.orEmpty(),
        backdrop = backdrop,
        confirmLabel = strings.savePlaylist,
        onNameChanged = onNameChanged,
        onDismiss = onCancel,
        onConfirm = onCreate,
    )
}

private val ROW_HEIGHT = 56.dp

/** The round window on a row. 40dp, the same as the avatar on a watch page. */
private val THUMBNAIL = 40.dp

/**
 * How tall the list may be before it scrolls.
 *
 * **Measured, not chosen.** The sheet caps itself at 45% of a portrait phone —
 * about 380dp — and everything else on it is fixed: a 46dp title, a 48dp Save
 * button, the gap between them and the home indicator's inset. 280 was the
 * first number here and the button came up below the fold on a household with
 * six playlists, which is the one control the sheet exists to reach.
 */
private val LIST_MAX_HEIGHT = 340.dp

/**
 * How much of the screen this sheet may take.
 *
 * More than the player's 45%, because this one is a *list*. At the player's
 * share it showed three rows of a household's dozen collections, which makes
 * scanning for one of them a scroll rather than a look.
 */
private const val SHEET_FRACTION = 0.62f

private val SHEET_SCRIM = Color.Black.copy(alpha = 0.5f)
