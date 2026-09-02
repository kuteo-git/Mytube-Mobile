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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mytube.app.ui.shell.tabContentPadding
import com.mytube.app.ui.shell.glassControl
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.home.BadgeBackground
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.shell.pressableGlassControl

/**
 * The collections this member keeps.
 *
 * A **tab**, in the middle of the bar where subscriptions used to be: this is a
 * list a household reaches for many times a sitting, and the list of channels it
 * follows is one somebody opens when looking for a channel. The saved shelf is
 * the first row here — one row, not two — which is where it belongs once there
 * is more than one collection.
 */
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    mediaBaseUrl: String,
    /**
     * Owned by the caller, because this screen is not composed while a playlist
     * is open — a `rememberLazyListState` here dies with it, and coming back
     * from a collection put the list at the top again. The same fault the tabs
     * had, and the same fix.
     */
    listState: LazyListState,
    /**
     * Null on the tab, which is not reached from anywhere.
     *
     * A back arrow there would be a control that leads nowhere — the setup
     * screen's rule, which is two things for the same reason: the first screen
     * of a fresh install and a row in Settings.
     */
    onBack: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Asked again on arrival, quietly. The ViewModel is held in the activity's
    // store and outlives this route, so a playlist made from the sheet on Home
    // would otherwise be missing here until the app was restarted.
    LaunchedEffect(Unit) { viewModel.refresh() }

    PlaylistsContent(
        state = state,
        mediaBaseUrl = mediaBaseUrl,
        listState = listState,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenSaved = onOpenSaved,
        onOpenPlaylist = onOpenPlaylist,
        onRetry = viewModel::refresh,
        onStartCreating = viewModel::startCreating,
    )
}

@Composable
fun PlaylistsContent(
    state: PlaylistsState,
    mediaBaseUrl: String,
    listState: LazyListState = rememberLazyListState(),
    onBack: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onRetry: () -> Unit,
    onStartCreating: () -> Unit,
) {
    val strings = LocalStrings.current

    // The create alert is **not** drawn here, and the two boxes that used to
    // wrap it are gone with it. Wrapping worked while this was a route, which
    // records its own layer; as a tab it is drawn inside `AppShell`'s
    // recording, and `glassSource` below is then a no-op — so both boxes were
    // inside the layer the alert would sample, and pressing "+" was
    // `EXC_BAD_ACCESS` in Skia's image-filter bounds walk. It is `App.kt`'s
    // now, beside the save sheet, which is outside every recording. The
    // charter records this crash twice already; this is the shape it takes
    // when a screen changes what it is used as.
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

            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                // A tab ends above the tab bar; a page ends above nothing but
                // the miniplayer. The arrow is what tells them apart, and it is
                // the same fact that decides both.
                contentPadding = if (onBack == null) tabContentPadding() else detailContentPadding(),
            ) {
                item(key = "title") {
                    // The "+" is in the title row, not at the end of the list.
                    // A member with twelve collections would otherwise have to
                    // scroll past all of them to make a thirteenth.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScreenTitle(strings.playlists, Modifier.weight(1f))
                        run {
                            Icon(
                                imageVector = PlusIcon,
                                contentDescription = strings.newPlaylist,
                                tint = Tokens.text,
                                modifier = Modifier
                                    .padding(end = Space.lg)
                                    .size(Size.iconButton)
                                    .clip(GlassRadius.control)
                                    .pressableGlassControl(
                                        GlassRadius.control,
                                        onClick = onStartCreating,
                                    )
                                    .padding(Space.sm),
                            )
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
                        // The shelf is one set, not a stack of collections.
                        stacked = false,
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

                if (ready.playlists.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(strings.noPlaylists, strings.noPlaylistsDetail)
                    }
                }
            }
        }

        if (onBack != null) DetailBack(onBack, strings.back)
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
    /** Whether to draw the edge of a card behind the picture. */
    stacked: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The picture, with the edge of another one showing above it.
        //
        // That strip is the whole reason a playlist row reads as a playlist
        // from across the screen rather than as a video that happens to have a
        // mark on it: it says there is something behind this picture. Narrower
        // than the thumbnail and rounded only at the top, because it is the top
        // of a card underneath — it is drawn, not stacked, since a real second
        // image behind this one would be a second request for two pixels of it.
        Column(
            Modifier.width(THUMBNAIL_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        if (stacked) {
            Box(
                Modifier
                    .padding(horizontal = STACK_INSET)
                    .fillMaxWidth()
                    .height(STACK_HEIGHT)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(Tokens.surfaceHover),
            )
            Spacer(Modifier.height(STACK_GAP))
        }
        Box(
            Modifier
                .fillMaxWidth()
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
            // Over a picture the mark sits on a **badge** in the corner, small
            // and dark — the web app's arrangement, and the same
            // [BadgeBackground] a video card's duration uses. One kind of badge
            // over a thumbnail, so two of them in one list do not disagree.
            //
            // A bare glyph would disappear against whatever the thumbnail
            // happens to be there, and the one thing a photograph cannot be
            // relied on to be is dark.
            //
            // With no picture there is nothing to be legible against, so the
            // mark stands alone in the middle of the empty pane: a badge over a
            // flat surface is a border drawn for its own sake.
            if (thumbnailPath.isEmpty()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Tokens.text,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Space.xs)
                        .clip(RoundedCornerShape(Radius.badge))
                        .background(BadgeBackground)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Tokens.text,
                        modifier = Modifier.size(BADGE_ICON),
                    )
                }
            }
        }
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

/** Small, because the badge is a label on a picture rather than a control. */
private val BADGE_ICON = 12.dp

/** The edge of the card behind this one. */
private val STACK_HEIGHT = 3.dp
private val STACK_INSET = 12.dp
private val STACK_GAP = 2.dp
