package com.mytube.app.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.mytube.app.ui.playlist.SaveTarget
import com.mytube.app.ui.i18n.VietnameseStrings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import com.mytube.app.ui.shell.DetailBack
import com.mytube.app.ui.shell.detailContentPadding
import com.mytube.app.ui.shell.GLASS_MARGIN
import com.mytube.app.ui.shell.GLASS_SHAPE
import com.mytube.app.ui.shell.SearchIcon
import com.mytube.app.ui.shell.glassSource
import com.mytube.app.ui.shell.liquidGlass
import com.mytube.app.ui.shell.EmptyState
import com.mytube.app.ui.theme.MytubeTheme
import com.mytube.app.ui.theme.Tokens
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.font.FontWeight
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.ui.watch.CloseIcon
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    /**
     * Opens the sheet asking which collections a result belongs in.
     *
     * Takes a target rather than a video because this screen has two kinds of
     * result: a library row carries an id, an upstream one carries only an
     * address and has no catalogue row until the sheet writes one.
     */
    onSaveToPlaylist: (SaveTarget) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val upstream by viewModel.upstream.collectAsStateWithLifecycle()
    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()
    val channelPasted by viewModel.channelPasted.collectAsStateWithLifecycle()

    // A pasted channel address is not a question, so it does not stay on this
    // screen. The web app learned the same thing and wrote down what it used to
    // do instead: run the text of the URL through `ytsearch20:`, spending a
    // counted upstream request hunting for an address.
    //
    // Leaving for the channel rather than pushing on top of search: back from a
    // channel already returns to Home, so there is no search page left behind
    // that would only redirect here again.
    LaunchedEffect(channelPasted) {
        if (channelPasted.isNotEmpty()) {
            viewModel.channelOpened()
            onOpenChannel(channelPasted)
        }
    }

    SearchContent(
        state = state,
        upstream = upstream,
        opening = opening,
        openFailed = openFailed,
        query = query,
        mediaBaseUrl = mediaBaseUrl,
        onBack = onBack,
        onType = viewModel::type,
        onClear = viewModel::clear,
        onRetry = viewModel::retry,
        onOpenVideo = onOpenVideo,
        // The row is written when the video is opened, not before, and the
        // screen navigates only once the gateway has answered with an id.
        onOpenExternal = { video -> viewModel.openExternal(video, onOpenVideo) },
        onLoadMoreUpstream = viewModel::loadMoreUpstream,
        onOpenChannel = onOpenChannel,
        onSaveToPlaylist = onSaveToPlaylist,
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
 * ## Two halves, and why they are both here
 *
 * This screen used to draw the library's answer alone, on the reasoning that
 * *"the gateway has one search route and it answers from the catalogue only"*.
 * That was simply wrong: `GET /api/discover` exists, the web app has always
 * called it, and its own note says why it is not a fallback — *"topics decide
 * what the feed offers, and searching is how someone deliberately looks past
 * that."* A library search that cannot reach past the library is half a search.
 *
 * The two halves fail independently and are drawn independently: an upstream
 * search that will not answer leaves a line under its own heading and takes
 * nothing else with it.
 */
@Composable
fun SearchContent(
    state: SearchState,
    upstream: UpstreamState,
    /** The `sourceUrl` of the upstream card being opened, or empty. */
    opening: String,
    /** True when the last attempt to open an upstream video failed. */
    openFailed: Boolean,
    query: String,
    mediaBaseUrl: String,
    onBack: () -> Unit,
    onType: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenExternal: (ExternalVideo) -> Unit,
    onLoadMoreUpstream: () -> Unit,
    /**
     * Open the channel behind a result's avatar.
     *
     * It was not passed at all, so every avatar in this list opened the video
     * instead — the fault the charter already records from the feed, repeated on
     * the one screen that was written after it: *"the avatar does nothing",
     * because something did happen and it was not what they aimed at.*
     */
    onOpenChannel: (String) -> Unit,
    onSaveToPlaylist: (SaveTarget) -> Unit = {},
) {
    val strings = LocalStrings.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    Surface(color = Tokens.bg, modifier = Modifier.fillMaxSize()) {
        // A Box, not a Column: the field is anchored to the bottom of the screen
        // and the results run the whole height under it.
        //
        // The field moved down with the search button that opens this screen.
        // While that button was in the top bar the field landing in the same
        // place was the point — the two never appeared together, so there was
        // never an inert box behind the one being typed into. The button is now
        // a capsule beside the tabs, and the field is where the thumb that
        // pressed it already is.
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    // The recording, and it wraps the results **only**.
                    //
                    // It used to wrap the whole screen, which was harmless while
                    // the only glass over this page was the miniplayer — a
                    // sibling, drawn by `App`. The field is not: it is a child of
                    // this screen, and a sampled backdrop inside the layer it
                    // samples is not a bad look, it is a crash. Measured on the
                    // simulator the moment search was opened: `SkRecordDraw` →
                    // `RenderNode::onDraw` → `SkRecordDraw`, all the way down,
                    // until the stack ran out. The charter records the same
                    // failure from the pull-to-refresh pane, and the same fix:
                    // draw the glass outside what it is reading.
                    .glassSource(),
            ) {
            // Padding on the list, not on a box around it — which is the
            // difference between content that *scrolls under* the arrow and the
            // field, and content clipped to the gap between them. Saved works
            // the first way; this screen was written the second, and a result
            // reaching either end stopped dead at an invisible edge instead of
            // passing behind the glass.
            val padding = searchContentPadding()
            val results = rememberLazyListState()

            // The keyboard goes away the moment the list moves.
            //
            // It comes up on arrival — somebody who pressed a search box meant
            // to type — and it has to leave on the first sign that typing is
            // over. Scrolling is that sign: half the screen is keyboard, and a
            // reader dragging results upward is reading, not typing. Dismissing
            // on scroll rather than on a tap outside, because on this screen a
            // tap outside is a tap on a result, which navigates away anyway.
            LaunchedEffect(results.isScrollInProgress) {
                if (results.isScrollInProgress) {
                    keyboard?.hide()
                    focus.clearFocus()
                }
            }

            // Asking for a larger upstream page as the bottom comes near.
            //
            // Everything read here comes from `layoutInfo`, which is snapshot
            // state and grows with the list. The feed learned this the hard way:
            // a `derivedStateOf` closing over a plain captured list compares
            // against the size it had on the first frame for ever, and the feed
            // stopped at 48 videos with nothing anywhere saying so.
            LaunchedEffect(results) {
                snapshotFlow {
                    val info = results.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= info.totalItemsCount - 3 && info.totalItemsCount > 0
                }
                    .distinctUntilChanged()
                    .collect { near -> if (near) onLoadMoreUpstream() }
            }

            // Videos already listed under "in your library" are not listed
            // again under "on YouTube". `inLibrary` is not what decides it: that
            // flag answers "is it on the disk", and the question here is "has
            // this row already appeared on this screen".
            val local = (state as? SearchState.Ready)?.videos.orEmpty()
            val localIds = local.map { it.id }.toSet()
            val remaining = upstreamResults(upstream).filter { it.id !in localIds }

            // A library half with nothing in it is not drawn at all.
            //
            // It used to say "Nothing here matches" under its heading,
            // which is a sentence with a job on a screen where the
            // library is the only answer — and this screen has two. Beside
            // a list of YouTube results it reads as a fault: a heading, a
            // line of apology, and the thing being looked for sitting
            // right underneath it.
            //
            // A *failed* library search still draws, because that is a
            // different sentence: "could not reach the library" is news,
            // and it comes with the retry.
            val libraryDrawn = state !is SearchState.Ready || local.isNotEmpty()

            when {
                // Nothing typed. One line for the whole screen rather than two
                // headings over two empty sections, which reads as a page that
                // failed to load.
                state is SearchState.Idle -> Box(Modifier.padding(padding)) {
                    EmptyState(strings.search, strings.searchPrompt)
                }

                state is SearchState.NeedsServer -> Box(Modifier.padding(padding)) {
                    EmptyState(strings.noServerTitle, strings.setTheAddress)
                }

                else -> LazyColumn(
                    state = results,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    // --- this library ------------------------------------
                    if (libraryDrawn) {
                        item(key = "heading-library") {
                            SectionHeading(
                                strings.inLibrary,
                                loading = state is SearchState.Searching,
                            )
                        }

                        when (state) {
                            is SearchState.Failed -> item(key = "local-failed") {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    Text(
                                        strings.couldNotReach,
                                        color = Tokens.text2,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        text = strings.tryAgain,
                                        color = Tokens.brand,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .clickable(onClick = onRetry)
                                            .padding(vertical = Space.sm),
                                    )
                                }
                            }

                            is SearchState.Ready -> {
                                items(local, key = { "local-${it.id}" }) { video ->
                                    VideoCard(
                                        video,
                                        mediaBaseUrl,
                                        strings,
                                        onClick = {
                                            // Hidden here as well as on scroll:
                                            // opening a video leaves this screen
                                            // composed underneath the watch layer,
                                            // and a keyboard left up is drawn over
                                            // that layer.
                                            keyboard?.hide()
                                            onOpenVideo(video.id)
                                        },
                                        onOpenChannel = { onOpenChannel(video.channel.id) },
                                        onSave = {
                                            onSaveToPlaylist(
                                                SaveTarget(video.id, saved = video.saved),
                                            )
                                        },
                                    )
                                }
                            }

                            else -> Unit
                        }
                    }

                    // --- and YouTube -------------------------------------
                    item(key = "heading-youtube") {
                        SectionHeading(
                            strings.onYouTube,
                            loading = upstream is UpstreamState.Searching ||
                                (upstream as? UpstreamState.Ready)?.loadingMore == true,
                            // Room above it only when there is something above
                            // it. With the library half hidden this heading is
                            // the first thing on the page.
                            modifier = if (libraryDrawn) {
                                Modifier.padding(top = Space.xl)
                            } else {
                                Modifier
                            },
                        )
                    }

                    when {
                        upstream is UpstreamState.Failed || openFailed ->
                            item(key = "upstream-failed") {
                                SectionNote(strings.youtubeUnreachable)
                            }

                        upstream is UpstreamState.Ready && remaining.isEmpty() ->
                            item(key = "upstream-empty") {
                                SectionNote(strings.noMoreResults)
                            }

                        else -> Unit
                    }

                    items(remaining, key = { "yt-${it.id}" }) { video ->
                        ExternalVideoCard(
                            video = video,
                            strings = strings,
                            opening = opening == video.sourceUrl,
                            onClick = {
                                keyboard?.hide()
                                onOpenExternal(video)
                            },
                            // An upstream result has no id yet; the address is
                            // what the sheet writes a row from on Save.
                            onSaveToPlaylist = {
                                keyboard?.hide()
                                onSaveToPlaylist(
                                    SaveTarget(videoId = "", sourceUrl = video.sourceUrl),
                                )
                            },
                        )
                    }
                }
            }
            }

            // The arrow alone, exactly as the saved shelf draws it. No title
            // beside it: the field at the bottom of this screen says what the
            // page is, and a heading reading "Search" over a list of results is
            // a word nobody needs twice.
            DetailBack(onBack, strings.back)

            SearchField(
                query = query,
                onType = onType,
                onClear = onClear,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The results in hand, whatever the upstream half is doing. */
private fun upstreamResults(state: UpstreamState): List<ExternalVideo> =
    (state as? UpstreamState.Ready)?.videos.orEmpty()

/**
 * A heading over one half of the answer.
 *
 * The spinner sits *in* the heading rather than over the section, so a half that
 * is still working says so without the other half's results moving.
 */
@Composable
private fun SectionHeading(text: String, loading: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Tokens.text2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (loading) {
            Spacer(Modifier.width(Space.sm))
            CircularProgressIndicator(
                color = Tokens.text2,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** One quiet line under a heading, for a section with nothing to list. */
@Composable
private fun SectionNote(text: String) {
    Text(
        text = text,
        color = Tokens.text2,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
    )
}

/**
 * The same padding Saved uses, plus the room this screen's field takes.
 *
 * The field is the only difference: it floats over the bottom of this page the
 * way the tab bar floats over a tab's, so the list ends above it rather than
 * behind it.
 */
@Composable
private fun searchContentPadding(): PaddingValues {
    val detail = detailContentPadding()
    val layout = LocalLayoutDirection.current
    return PaddingValues(
        top = detail.calculateTopPadding(),
        start = detail.calculateStartPadding(layout),
        end = detail.calculateEndPadding(layout),
        bottom = detail.calculateBottomPadding() + SEARCH_FIELD_ROW,
    )
}

/**
 * How much room the field takes at the bottom, for the results to stop above.
 *
 * Read by `App` as well, which floats the miniplayer over this screen and has to
 * rest it on top of the field rather than across it.
 *
 * The pill and nothing else. It carried a 16dp margin above and below, which
 * put the miniplayer 16dp higher over this screen than it sits over the tab bar
 * — the same bar, the same player, two different gaps. The tab bar has no margin
 * over it either; both rest straight on the navigation inset.
 *
 * Not the navigation inset itself: that is added by the field and by the list's
 * own padding, since a phone with buttons reports zero for it and a hard-coded
 * 34dp would be an iPhone's home indicator drawn on a phone that has none.
 */
internal val SEARCH_FIELD_ROW = 48.dp

@Composable
private fun SearchField(
    query: String,
    onType: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // The keyboard comes up on arrival. Somebody who pressed a search button
    // meant to type; making them press a second time is a tap this screen exists
    // to save them.
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier
            .fillMaxWidth()
            // imePadding before the navigation inset, so the field rides up on
            // the keyboard and rests on the home indicator when there is none.
            // Both, and in this order: the keyboard's inset already contains the
            // navigation one while it is up, and adding them the other way round
            // left a 34dp gap under the field on an iPhone.
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = GLASS_MARGIN)
            // 48dp, not the 40dp a chip is. This is the one control the screen
            // exists for and it is the last thing a thumb reaches on the way
            // down the phone; a chip's height is sized for a row of them.
            .height(SEARCH_FIELD_ROW)
            // The same material as the chips and the bars, and for the same
            // reason: it floats over a page, outside the layer the screen
            // records, so it can sample rather than paint.
            .liquidGlass(GLASS_SHAPE)
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SearchIcon,
            contentDescription = null,
            tint = Tokens.text2,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Space.sm))
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
                    // The keyboard says Search, so pressing it has to mean
                    // something. The query is already sent — every keystroke is,
                    // 300ms behind the finger — so what is left for the key to do
                    // is get out of the way of the answer, which is the same
                    // thing scrolling the results does.
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                    ),
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

private fun upstreamSample(id: String, title: String) = ExternalVideo(
    id = id,
    title = title,
    channelName = "Marques Brownlee",
    durationSeconds = 512,
    viewCount = 1_200_000,
    thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    sourceUrl = "https://www.youtube.com/watch?v=$id",
    inLibrary = false,
)

@Composable
private fun preview(
    state: SearchState,
    query: String,
    upstream: UpstreamState = UpstreamState.Idle,
    opening: String = "",
    openFailed: Boolean = false,
) {
    MytubeTheme {
        SearchContent(
            state = state,
            upstream = upstream,
            opening = opening,
            openFailed = openFailed,
            query = query,
            mediaBaseUrl = "",
            onBack = {},
            onType = {},
            onClear = {},
            onRetry = {},
            onOpenVideo = {},
            onOpenExternal = {},
            onLoadMoreUpstream = {},
            onOpenChannel = {},
        )
    }
}

/** Both halves answered, which is the ordinary case. */
@Preview
@Composable
private fun SearchResultsPreview() =
    preview(
        state = SearchState.Ready("nothing", listOf(sample("a", "Nothing Phone (4a) Pro"))),
        query = "nothing",
        upstream = UpstreamState.Ready(
            listOf(upstreamSample("b", "Nothing Phone (4a) Pro — the honest review")),
        ),
    )

/**
 * The library answered and YouTube did not.
 *
 * The state that matters most and is hardest to reach on a device: it is the one
 * this screen is written to survive, and the half that worked has to still be
 * readable.
 */
@Preview
@Composable
private fun SearchUpstreamFailedPreview() =
    preview(
        state = SearchState.Ready("nothing", listOf(sample("a", "Nothing Phone (4a) Pro"))),
        query = "nothing",
        upstream = UpstreamState.Failed,
    )

/** A card being written into the catalogue, which takes seconds and says so. */
@Preview
@Composable
private fun SearchOpeningPreview() =
    preview(
        state = SearchState.Ready("nothing", emptyList()),
        query = "nothing",
        upstream = UpstreamState.Ready(listOf(upstreamSample("b", "Nothing Phone (4a) Pro"))),
        opening = "https://www.youtube.com/watch?v=b",
    )

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
