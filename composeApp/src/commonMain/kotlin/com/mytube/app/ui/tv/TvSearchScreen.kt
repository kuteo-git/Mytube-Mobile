package com.mytube.app.ui.tv

import com.mytube.app.ui.shell.LocalPageEntry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.mytube.app.ui.playlist.SaveTarget
import com.mytube.app.ui.playlist.saveTargetOf
import com.mytube.app.ui.home.videoMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxWidth
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.Strings
import com.mytube.app.ui.i18n.VietnameseStrings
import com.mytube.app.ui.search.SearchState
import com.mytube.app.ui.search.SearchViewModel
import com.mytube.app.ui.search.rememberResultsState
import com.mytube.app.ui.shell.awaitPlaced
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import com.mytube.app.ui.shell.FOCUS_MAX_OUTSET
import com.mytube.app.ui.shell.GlassButton
import com.mytube.app.ui.shell.LocalTelevision
import com.mytube.app.ui.shell.pressableGlassControl
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.home.stroke
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Search on a television: the query and a keyboard held still on the left, the
 * results as a list scrolling on the right — Google TV's own search layout
 * rather than YouTube's stacked one, for the reason given in [TvSearchContent].
 *
 * The phone's search screen is a field at the bottom of the page waiting for
 * the system keyboard. On a Google TV that keyboard opens over the results it
 * is filling, and on the household's Chromecast it asked to be typed on from a
 * phone. Here the keyboard is part of the page, the results fill in below it as
 * the query grows — the ViewModel already searches as somebody types — and the
 * remote's microphone can stand in for both.
 *
 * The ViewModel is the phone's: the same debounce, the same answer from
 * YouTube, the same writing of a catalogue row before a result is opened.
 */
@Composable
fun TvSearchScreen(
    viewModel: SearchViewModel,
    mediaBaseUrl: String,
    /** The app's language, which the recogniser listens in — see [rememberVoiceSearch]. */
    languageCode: String,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onSaveToPlaylist: (SaveTarget) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()
    val asking by viewModel.asking.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val channelPasted by viewModel.channelPasted.collectAsStateWithLifecycle()

    // A channel's address heard or typed is not a question, as on the phone.
    LaunchedEffect(channelPasted) {
        if (channelPasted.isNotEmpty()) {
            viewModel.channelOpened()
            onOpenChannel(channelPasted)
        }
    }

    TvSearchContent(
        query = query,
        state = state,
        suggestions = suggestions,
        asking = asking,
        opening = opening,
        openFailed = openFailed,
        mediaBaseUrl = mediaBaseUrl,
        startLayout = if (languageCode == "vi") KeyboardLayout.Vietnamese else KeyboardLayout.Latin,
        voice = rememberVoiceSearch(languageCode) { heard -> viewModel.type(heard) },
        onType = viewModel::type,
        onRetry = viewModel::retry,
        onOpen = { viewModel.open(it, onOpenVideo) },
        onMore = viewModel::loadMore,
        onNotInterested = viewModel::notInterested,
        onSaveToPlaylist = onSaveToPlaylist,
    )
}

@Composable
fun TvSearchContent(
    query: String,
    state: SearchState,
    /** Home's first page, listed while nothing is typed and under an empty answer. */
    suggestions: List<Video>,
    /** A question is out: the query bar spins. */
    asking: Boolean,
    /** The result being written into the catalogue, by address, or empty. */
    opening: String,
    /** The last result pressed could not be written; said over the list, as on the phone. */
    openFailed: Boolean,
    mediaBaseUrl: String,
    startLayout: KeyboardLayout,
    /** Null where there is no recogniser, and then no microphone is drawn. */
    voice: (() -> Unit)?,
    onType: (String) -> Unit,
    onRetry: () -> Unit,
    onOpen: (Video) -> Unit,
    onMore: () -> Unit,
    onNotInterested: (Video) -> Unit,
    /** A long press of OK on a row — see `TvThumbnail`. */
    onSaveToPlaylist: (SaveTarget) -> Unit,
) {
    val strings = LocalStrings.current
    var layout by remember { mutableStateOf(startLayout) }
    // The app's own requester when there is one, so the page it hands focus
    // to as it opens is the keyboard rather than the microphone above it.
    val firstKey = LocalPageEntry.current ?: remember { FocusRequester() }
    val results = remember { FocusRequester() }
    val latestQuery by rememberUpdatedState(query)
    val listState = rememberResultsState(state)
    val scope = rememberCoroutineScope()

    // The keyboard is where a search starts, so it is where focus starts.
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    // **Focus held by the list stays in the list when the list is replaced.**
    // Search on the keyboard sends focus across to whatever is showing — the
    // suggestions while the first question is out, the last answer while a
    // refined one is — and the answer arriving removes the focused row. A
    // focused node that leaves the composition takes focus to the rail, so the
    // list hands it to its new first row instead. Held until the keyboard has
    // focus again, which is somebody choosing to type.
    var listHeld by remember { mutableStateOf(false) }
    val focusList: () -> Unit = {
        // `requestScrollToItem`, not `scrollToItem`: a new answer's list state
        // has had no layout yet, and `scrollToItem` on one waits for ever.
        listState.requestScrollToItem(0)
        scope.launch {
            awaitPlaced()
            runCatching { results.requestFocus() }
        }
    }
    LaunchedEffect((state as? SearchState.Ready)?.query ?: state, suggestions) {
        if (listHeld) focusList()
    }

    // Two columns: the query and the keyboard held still on the left, the
    // results scrolling on their own on the right. Stacked, as YouTube's TV
    // app stacks them, the Vietnamese keyboard's six rows pushed the results
    // below the screen, and walking down the keyboard scrolled the query out of
    // sight — measured on the television, typing a tone with no way to see
    // what it had been put on.
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(top = Space.sm)
                .onFocusChanged { if (it.hasFocus) listHeld = false },
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            QueryBar(query, asking, strings, voice)
            TvKeyboard(
                layout = layout,
                strings = strings,
                firstKey = firstKey,
                modifier = Modifier.padding(start = FOCUS_MAX_OUTSET),
                onKey = { key ->
                    when (key) {
                        TvKey.Symbols -> layout =
                            if (layout == KeyboardLayout.Symbols) startLayout else KeyboardLayout.Symbols
                        TvKey.Language -> layout = otherLanguage(layout)
                        // Done typing: across to the top of what is listed. From
                        // the top, because a row scrolled out of sight is not
                        // composed and cannot take focus.
                        TvKey.Search -> {
                            listHeld = true
                            focusList()
                        }
                        else -> {
                            val next = typed(latestQuery, key)
                            if (next != latestQuery) onType(next)
                        }
                    }
                },
            )
        }
        Spacer(Modifier.width(Space.xl))
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged { if (it.hasFocus) listHeld = true },
            state = listState,
            contentPadding = PaddingValues(top = Space.sm, bottom = Space.xl),
        ) {
            val found = (state as? SearchState.Ready)?.videos.orEmpty()
            val row: LazyListScope.(List<Video>, String, Boolean) -> Unit = { videos, prefix, suggested ->
                itemsIndexed(videos, key = { _, video -> prefix + video.id }) { index, video ->
                    // A few rows before the end of an answer asks for more, the
                    // shelves' rule. Suggestions are one page and stop there.
                    if (!suggested && index >= videos.size - 4) LaunchedEffect(videos.size) { onMore() }
                    TvVideoRow(
                        video = video,
                        mediaBaseUrl = mediaBaseUrl,
                        strings = strings,
                        opening = opening.isNotEmpty() && opening == video.sourceUrl,
                        onClick = { onOpen(video) },
                        // Remembered: every key of the keyboard recomposes this
                        // list. A result may have no id yet; the sheet writes the
                        // row from the address on Save, as the phone's search does.
                        menu = remember(video, strings, suggested) {
                            videoMenu(
                                strings,
                                onSave = { onSaveToPlaylist(saveTargetOf(video)) },
                                onNotInterested = if (suggested) ({ onNotInterested(video) }) else null,
                            )
                        },
                        modifier = if (index == 0) Modifier.focusRequester(results) else Modifier,
                    )
                }
            }
            if (openFailed) item(key = "open-failed") { Note(strings.youtubeUnreachable) }
            when (state) {
                SearchState.NeedsServer -> item(key = "server") { Note(strings.noServerTitle) }
                is SearchState.Failed -> item(key = "failed") {
                    NoAnswer(strings.youtubeUnreachable) {
                        GlassButton(label = strings.tryAgain, onClick = onRetry)
                    }
                }
                is SearchState.Ready -> if (found.isEmpty()) {
                    item(key = "none") { NoAnswer(strings.noResults) { Note(strings.noResultsDetail) } }
                } else {
                    row(found, "", false)
                }
                SearchState.Idle -> if (suggestions.isEmpty() && query.isBlank()) {
                    item(key = "prompt") { Note(strings.searchPrompt) }
                }
            }
            if (found.isEmpty() && state !is SearchState.NeedsServer && suggestions.isNotEmpty()) {
                item(key = "suggested-title") { TvSectionTitle(strings.suggestedVideos) }
                row(suggestions, "suggested-", true)
            }
        }
    }
}

/**
 * What is being searched for, read back — and the microphone beside it.
 *
 * The box is not a field: there is nothing to put a caret in with a remote, and
 * a focus stop that does nothing is a dead button. The keyboard under it types.
 */
@Composable
private fun QueryBar(query: String, asking: Boolean, strings: Strings, voice: (() -> Unit)?) {
    Row(
        Modifier.width(SEARCH_COLUMN).padding(start = FOCUS_MAX_OUTSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (voice != null) {
            Box(
                Modifier
                    .size(BAR_HEIGHT)
                    .pressableGlassControl(CircleShape, onClick = voice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MicIcon, strings.voiceSearch, tint = Tokens.text, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(Space.md))
        }
        Row(
            Modifier
                .weight(1f)
                .height(BAR_HEIGHT)
                .background(Tokens.surface, RoundedCornerShape(percent = 50))
                .padding(horizontal = Space.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = query.ifEmpty { strings.searchHint },
                color = if (query.isEmpty()) Tokens.text2 else Tokens.text,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Where the phone's clear button is. Nothing here takes focus: the
            // keyboard's own delete is how a remote clears.
            if (asking) {
                Spacer(Modifier.width(Space.md))
                CircularProgressIndicator(color = Tokens.text2, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * An answer with nothing in it, or none at all — tall enough to read as the
 * answer rather than as a line over the suggestions under it.
 */
@Composable
private fun NoAnswer(title: String, detail: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(NO_ANSWER_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Tokens.text, fontSize = 20.sp, lineHeight = 24.sp)
        detail()
    }
}

private val NO_ANSWER_HEIGHT = 200.dp

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = Tokens.text2,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        modifier = Modifier.padding(start = FOCUS_MAX_OUTSET),
    )
}

/** The query bar's height: YouTube's TV app's, 36dp, grown to a target. */
private val BAR_HEIGHT = 48.dp

/** The left column: the keyboard's width, which the query bar matches. */
private val SEARCH_COLUMN = 460.dp

private val MicIcon: ImageVector by lazy {
    stroke("Mic") {
        moveTo(12f, 3f)
        curveTo(13.7f, 3f, 15f, 4.3f, 15f, 6f); lineTo(15f, 11f)
        curveTo(15f, 12.7f, 13.7f, 14f, 12f, 14f)
        curveTo(10.3f, 14f, 9f, 12.7f, 9f, 11f); lineTo(9f, 6f)
        curveTo(9f, 4.3f, 10.3f, 3f, 12f, 3f); close()
        moveTo(5.5f, 11f)
        curveTo(5.5f, 14.6f, 8.4f, 17.5f, 12f, 17.5f)
        curveTo(15.6f, 17.5f, 18.5f, 14.6f, 18.5f, 11f)
        moveTo(12f, 17.5f); lineTo(12f, 21f)
    }
}

// --- previews ---------------------------------------------------------------

private val previewVideos = (1..5).map {
    Video(
        id = "v$it",
        title = "A video about the thing searched for, number $it",
        channel = Channel(id = "c", name = "Some Channel", handle = "", avatarPath = ""),
        durationSeconds = 600,
        viewCount = 9_000,
        publishedAt = "2026-10-01T08:00:00Z",
        thumbnailPath = "",
        sourceUrl = "https://www.youtube.com/watch?v=v$it",
        inLibrary = it == 1,
    )
}

@Composable
private fun preview(
    query: String,
    state: SearchState,
    vietnamese: Boolean = false,
    asking: Boolean = false,
) = MytubeTheme {
    CompositionLocalProvider(
        LocalTelevision provides true,
        LocalStrings provides if (vietnamese) VietnameseStrings else LocalStrings.current,
    ) {
        Box(Modifier.fillMaxSize().background(Tokens.bg)) {
            TvSearchContent(
                query = query,
                state = state,
                suggestions = previewVideos.map { it.copy(id = "s${it.id}", inLibrary = true) },
                asking = asking,
                opening = "",
                openFailed = false,
                mediaBaseUrl = "",
                startLayout = if (vietnamese) KeyboardLayout.Vietnamese else KeyboardLayout.Latin,
                voice = {},
                onType = {},
                onRetry = {},
                onOpen = {},
                onMore = {},
                onNotInterested = {},
                onSaveToPlaylist = {},
            )
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvSearchIdlePreview() = preview("", SearchState.Idle)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvSearchReadyPreview() = preview("iphone", SearchState.Ready("iphone", previewVideos))

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvSearchSearchingPreview() = preview("iph", SearchState.Idle, asking = true)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvSearchFailedPreview() = preview("iphone", SearchState.Failed("gateway answered 502"))

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun TvSearchVietnamesePreview() = preview("sơn tùng", SearchState.Ready("sơn tùng", emptyList()), vietnamese = true)
