package com.mytube.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.home.VideoCard
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.watch.BackIcon
import com.mytube.app.ui.watch.CloseIcon
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    SearchContent(
        state = state,
        query = query,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onType = viewModel::type,
        onClear = viewModel::clear,
        onRetry = viewModel::retry,
        onOpenVideo = onOpenVideo,
    )
}

/**
 * Search over this library.
 *
 * ## Why the field replaces the top bar rather than sitting under it
 *
 * The bar's search box is what somebody presses to get here, so the field they
 * land on has to be where that box was. Drawing a second field below the first
 * would leave two on screen, one of them inert.
 *
 * ## What this does not do
 *
 * The web app splits results into "In your library" and "On YouTube". The
 * gateway has one search route and it answers from the catalogue only, so there
 * is no second half to draw — and inventing a heading over a single list would
 * promise a second one that never arrives.
 */
@Composable
fun SearchContent(
    state: SearchState,
    query: String,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onType: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val strings = LocalStrings.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .background(Tokens.statusBar)
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
            SearchBar(query, onBack, onType, onClear)

            when (state) {
                is SearchState.Idle -> EmptyState(strings.search, strings.searchPrompt)

                is SearchState.Searching -> Box(
                    Modifier.fillMaxWidth().padding(Space.xl),
                    Alignment.Center,
                ) { CircularProgressIndicator() }

                is SearchState.NeedsServer -> EmptyState(
                    strings.noServerTitle,
                    strings.setTheAddress,
                )

                is SearchState.Failed -> Column(
                    Modifier.fillMaxWidth().padding(Space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(strings.couldNotReach, color = Tokens.text, fontSize = 16.sp)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        text = strings.tryAgain,
                        color = Tokens.brand,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable(onClick = onRetry).padding(Space.sm),
                    )
                }

                is SearchState.Ready -> if (state.videos.isEmpty()) {
                    EmptyState(strings.noResults, strings.noResultsDetail)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.videos, key = { it.id }) { video ->
                            VideoCard(
                                video,
                                mediaBaseUrl,
                                strings,
                                onClick = { onOpenVideo(video.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onBack: () -> Unit,
    onType: (String) -> Unit,
    onClear: () -> Unit,
) {
    val strings = LocalStrings.current
    val focus = remember { FocusRequester() }

    // The keyboard comes up on arrival. Somebody who pressed a search box meant
    // to type; making them press a second time is a tap this screen exists to
    // save them.
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        Modifier.fillMaxWidth().height(Size.topBar).padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BackIcon, strings.back, tint = Tokens.text, modifier = Modifier.size(24.dp))
        }

        Row(
            Modifier
                .weight(1f)
                .height(Size.chip + 8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .border(1.dp, Tokens.line, RoundedCornerShape(percent = 50))
                .background(Tokens.surfaceInput)
                .padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                // BasicTextField, not Material's: the design system's input is a
                // pill with its own border and background, and TextField brings
                // a container, a label slot and an indicator line that would all
                // have to be switched off.
                BasicTextField(
                    value = query,
                    onValueChange = onType,
                    singleLine = true,
                    textStyle = TextStyle(color = Tokens.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(Tokens.brand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (query.isEmpty()) {
                    Text(strings.searchHint, color = Tokens.text2, fontSize = 14.sp)
                }
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(Space.sm))
                Icon(
                    imageVector = CloseIcon,
                    contentDescription = strings.close,
                    tint = Tokens.text2,
                    modifier = Modifier.size(20.dp).clickable(onClick = onClear),
                )
            }
        }
    }
}

// --- previews ---------------------------------------------------------------

private fun sample(id: String, title: String) = Video(
    id = id,
    title = title,
    channel = Channel(
        id = "c",
        name = "relab",
        handle = "@relab",
        avatarPath = "channels/c/avatar.jpg",
    ),
    durationSeconds = 140,
    viewCount = 2_400,
    publishedAt = "2026-08-23T05:35:52Z",
    thumbnailPath = "thumbnails/$id.jpg",
)

@Composable
private fun preview(state: SearchState, query: String) {
    MytubeTheme {
        SearchContent(
            state = state,
            query = query,
            mediaBaseUrl = "",
            onBack = {},
            onType = {},
            onClear = {},
            onRetry = {},
            onOpenVideo = {},
        )
    }
}

@Preview
@Composable
private fun SearchResultsPreview() =
    preview(SearchState.Ready("nothing", listOf(sample("a", "Nothing Phone (4a) Pro"))), "nothing")

/** Arrived and typed nothing — the state this screen always opens on. */
@Preview
@Composable
private fun SearchIdlePreview() = preview(SearchState.Idle, "")

@Preview
@Composable
private fun SearchEmptyPreview() = preview(SearchState.Ready("zzz", emptyList()), "zzz")

@Preview
@Composable
private fun SearchVietnamesePreview() {
    CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
        preview(SearchState.Idle, "")
    }
}
