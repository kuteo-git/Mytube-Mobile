package com.mytube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import com.mytube.app.ui.theme.Tokens
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.watch.MiniPlayer
import com.mytube.app.ui.watch.WatchState
import androidx.compose.runtime.Composable
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mytube.app.AppContainer
import com.mytube.app.ui.home.HomeScreen
import com.mytube.app.ui.home.HomeViewModel
import com.mytube.app.ui.shell.AppShell
import com.mytube.app.ui.shell.Tab
import com.mytube.app.ui.watch.WatchLayer
import com.mytube.app.ui.watch.WatchScreen
import com.mytube.app.ui.watch.WatchViewModel
import com.mytube.app.ui.settings.ServerSetupScreen
import com.mytube.app.ui.settings.ServerSetupViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.mytube.app.ui.i18n.LocalStrings
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.channel.ChannelScreen
import com.mytube.app.ui.channel.ChannelViewModel
import com.mytube.app.ui.history.HistoryScreen
import com.mytube.app.ui.history.HistoryViewModel
import com.mytube.app.ui.i18n.Language
import com.mytube.app.ui.i18n.deviceLanguage
import com.mytube.app.domain.repository.FeedMix
import com.mytube.app.ui.saved.SavedScreen
import com.mytube.app.ui.saved.SavedViewModel
import com.mytube.app.ui.search.SearchScreen
import com.mytube.app.ui.search.SearchViewModel
import com.mytube.app.ui.settings.SettingsScreen
import com.mytube.app.ui.subscriptions.SubscriptionsScreen
import com.mytube.app.ui.subscriptions.SubscriptionsViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.mytube.app.ui.theme.MytubeTheme

/**
 * Which screen is showing.
 *
 * Four of them so far, and no navigation library. Compose Navigation earns its
 * keep with a back stack, deep links and state saved across several levels; none
 * of that exists yet, and choosing it now would be picking a dependency before
 * the shape it serves is known.
 *
 * The watch screen is the case that would normally force the decision, and it
 * argues the other way. Per the server charter it is *a layer over the tab
 * underneath*, dismissed by dragging down — so it is not a destination that
 * replaces what came before, and expressing it as one would mean fighting the
 * library to keep the tab composed while the layer is dragged off it.
 */
private sealed interface Route {
    data object Deciding : Route
    data object Setup : Route
    data object Home : Route
    data object Search : Route
    data object Saved : Route
    data class Channel(val channelId: String) : Route
}

/**
 * The video being watched, and whether it is collapsed to the miniplayer.
 *
 * Not a route. A route is somewhere you are, and this is something that keeps
 * playing while you go elsewhere — modelling it as a destination is what made
 * leaving the screen the same act as stopping the video.
 */
private data class WatchSession(
    val videoId: String,
    val minimised: Boolean = false,
    /** Arrived at by pressing next, so it opens at zero rather than resuming. */
    val startAtBeginning: Boolean = false,
)

/**
 * The root.
 *
 * Takes the container rather than reaching for a global: every dependency any
 * screen has arrives through this one argument, which is what makes the graph
 * something a reader can follow and a test can replace.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun App(container: AppContainer) {
    // Null until the stored choice has been read. Nothing is drawn before then:
    // showing English for a frame to somebody who chose Vietnamese is the exact
    // flicker the web app avoided by reading its preference at module load.
    var language: Language? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        val stored = container.serverRepository.language()
        // Empty means "follow the device", and it stays empty rather than being
        // resolved and written back. Storing what the phone happened to be set
        // to on the first launch freezes it there for ever.
        language = if (stored.isEmpty()) deviceLanguage() else Language.forTag(stored)
    }

    val chosen = language ?: return

    CompositionLocalProvider(LocalStrings provides chosen.strings) {
        MytubeTheme {
            var route: Route by remember { mutableStateOf(Route.Deciding) }
            var tab by remember { mutableStateOf(Tab.Home) }
            var baseUrl by remember { mutableStateOf("") }
            // Null when nothing is playing. Held above the routes so that
            // changing tab, or stepping into settings, does not take the video
            // with it.
            var watching: WatchSession? by remember { mutableStateOf(null) }
            // What has been watched this sitting, oldest first.
            //
            // Held here rather than in the watch ViewModel because it is a fact
            // about the *sitting*: a ViewModel is rebuilt for every video and
            // could not remember what came before it. Not persisted — "previous"
            // means previous in this sitting, and offering to go back to
            // something watched last week is not what the button says.
            val trail = remember { mutableStateListOf<String>() }
            val scope = rememberCoroutineScope()
            // Null until the server answers. Read once for the process: it is
            // one setting for the household and nothing else in the app changes
            // it, so re-reading on every visit to Settings would be a request
            // for an answer already held.
            var feedMix: FeedMix? by remember { mutableStateOf(null) }
            LaunchedEffect(baseUrl) {
                if (baseUrl.isNotBlank()) {
                    feedMix = runCatching { container.videoRepository.feedMix() }.getOrNull()
                }
            }

            // Which screen opens is a question for the settings store, and that
            // cannot be answered during composition. Nothing is drawn until it is:
            // showing the setup form for one frame to somebody who configured this
            // months ago reads as the app having forgotten.
            LaunchedEffect(route) {
                baseUrl = container.serverRepository.baseUrl()
                if (route is Route.Deciding) {
                    route = if (baseUrl.isBlank()) Route.Setup else Route.Home
                }
            }

            // What the system back gesture means, decided in one place.
            //
            // One handler rather than one per screen, because "back" is a
            // question about the *whole* navigation state and every screen only
            // knows its own part: the watch layer sits over a tab, the tab sits
            // inside a route, and a handler on any of them would have to guess
            // about the others. Ordered from the most recently opened thing
            // outwards, which is what somebody pressing back means.
            //
            // Disabled at the last step rather than doing nothing: a handler
            // that swallows back on Home would trap the app open, and leaving it
            // to the system is what makes the gesture close it as every other
            // app does.
            // A *collapsed* video is deliberately not counted. The miniplayer
            // survives changing tabs and is closed by its own X; back closing it
            // would make the one control that means "stop" ambiguous. And every
            // condition here must have a branch below — enabling the handler
            // with nothing to do swallows the gesture, which traps the app open.
            // Measured: it did, for one build.
            val backable = watching?.minimised == false ||
                route !is Route.Home ||
                tab != Tab.Home
            BackHandler(backable) {
                val session = watching
                when {
                    // Expanded video: collapse it rather than close it. The
                    // sound carries on, which is what the drag down does too.
                    session != null && !session.minimised ->
                        watching = session.copy(minimised = true)

                    // A screen opened from a tab.
                    route !is Route.Home -> route = Route.Home

                    // A tab other than the first.
                    tab != Tab.Home -> tab = Tab.Home
                }
            }

            // Everything stacks in one Box so the player can sit over whichever
            // screen is showing. It is the miniplayer that needs this, not the
            // watch screen: the video keeps playing while somebody searches or
            // opens a channel, and the bar has to be reachable from there.
            Box(Modifier.fillMaxSize()) {
                when (val current = route) {
                    is Route.Deciding -> Unit

                    is Route.Setup -> ServerSetupScreen(
                        viewModel = viewModel { ServerSetupViewModel(container.serverRepository) },
                        onDone = { route = Route.Home },
                    )

                    // The four tabs and the watch screen are one branch. The
                    // watch screen is a layer *over* the tab, so the tab has to be
                    // composed underneath it — otherwise dragging down reveals an
                    // empty background and the feed snaps in at the end. The
                    // miniplayer needs the same thing for a different reason: it
                    // sits on the tab bar while somebody browses another tab.
                    is Route.Home -> AppShell(
                        current = tab,
                        onSelect = { tab = it },
                        onSearch = { route = Route.Search },
                    ) {
                            when (tab) {
                                Tab.Home -> HomeScreen(
                                    // Keyed on the address: changing it builds a new
                                    // HomeViewModel, because the old one holds a
                                    // feed fetched from somewhere else.
                                    viewModel = viewModel(key = "home-$baseUrl") {
                                        HomeViewModel(container.videoRepository)
                                    },
                                    mediaBaseUrl = baseUrl,
                                    onOpenSettings = { tab = Tab.Settings },
                                    onOpenVideo = { watching = WatchSession(it) },
                                    onOpenChannel = { route = Route.Channel(it) },
                                )

                                Tab.Subscriptions -> SubscriptionsScreen(
                                    viewModel = viewModel(key = "subs-$baseUrl") {
                                        SubscriptionsViewModel(container.videoRepository)
                                    },
                                    mediaBaseUrl = baseUrl,
                                    onOpenSettings = { tab = Tab.Settings },
                                    // The channel screen does not exist yet, so
                                    // pressing a row does nothing rather than
                                    // pretending. Drawn anyway: the list is the
                                    // answer to "who do I follow", which is most of
                                    // what this tab is for.
                                    onOpenChannel = { route = Route.Channel(it) },
                                )

                                Tab.History -> HistoryScreen(
                                    viewModel = viewModel(key = "history-$baseUrl") {
                                        HistoryViewModel(container.videoRepository)
                                    },
                                    mediaBaseUrl = baseUrl,
                                    onOpenSettings = { tab = Tab.Settings },
                                    onOpenVideo = { watching = WatchSession(it) },
                                )

                                Tab.Settings -> SettingsScreen(
                                    baseUrl = baseUrl,
                                    language = chosen,
                                    feedMix = feedMix,
                                    onOpenServer = { route = Route.Setup },
                                    onOpenSaved = { route = Route.Saved },
                                    onChangeMix = { next ->
                                        // Drawn immediately, sent after: a
                                        // slider that waits for a round trip
                                        // before moving is one that feels stuck.
                                        feedMix = next
                                        scope.launch {
                                            runCatching {
                                                container.videoRepository.saveFeedMix(next)
                                            }
                                        }
                                    },
                                    onPickLanguage = { picked ->
                                        language = picked
                                        scope.launch {
                                            container.serverRepository.setLanguage(picked.code)
                                        }
                                    },
                                )
                            }
                        }

                    is Route.Saved -> SavedScreen(
                    viewModel = viewModel(key = "saved-$baseUrl") {
                        SavedViewModel(container.videoRepository)
                    },
                    mediaBaseUrl = baseUrl,
                    onBack = { route = Route.Home },
                    onOpenSettings = { route = Route.Setup },
                    onOpenVideo = { watching = WatchSession(it) },
                    onOpenChannel = { route = Route.Channel(it) },
                )

                is Route.Search -> SearchScreen(
                        viewModel = viewModel(key = "search-$baseUrl") {
                            SearchViewModel(container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = Route.Home },
                        onOpenVideo = { watching = WatchSession(it) },
                    )

                    is Route.Channel -> ChannelScreen(
                        viewModel = viewModel(key = "channel-${current.channelId}") {
                            ChannelViewModel(current.channelId, container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = Route.Home },
                        onOpenSettings = { route = Route.Setup },
                        onOpenVideo = { watching = WatchSession(it) },
                    )
                }

                val session = watching
                when {
                    // Expanded video: collapse it rather than close it. The
                    // sound carries on, which is what the drag down does too.
                    session != null && !session.minimised ->
                        watching = session.copy(minimised = true)

                    // A screen opened from a tab.
                    route !is Route.Home -> route = Route.Home

                    // A tab other than the first.
                    tab != Tab.Home -> tab = Tab.Home
                }
            }

            // Everything stacks in one Box so the player can sit over whichever
            // screen is showing. It is the miniplayer that needs this, not the
            // watch screen: the video keeps playing while somebody searches or
            // opens a channel, and the bar has to be reachable from there.
            Box(Modifier.fillMaxSize()) {
                when (val current = route) {
                    is Route.Deciding -> Unit

                    is Route.Setup -> ServerSetupScreen(
                        viewModel = viewModel { ServerSetupViewModel(container.serverRepository) },
                        onDone = { route = Route.Home },
                    )

                    // The four tabs and the watch screen are one branch. The
                    // watch screen is a layer *over* the tab, so the tab has to be
                    // composed underneath it — otherwise dragging down reveals an
                    // empty background and the feed snaps in at the end. The
                    // miniplayer needs the same thing for a different reason: it
                    // sits on the tab bar while somebody browses another tab.
                    is Route.Home -> AppShell(
                        current = tab,
                        onSelect = { tab = it },
                        onSearch = { route = Route.Search },
                    ) {
                            when (tab) {
                                Tab.Home -> HomeScreen(
                                    // Keyed on the address: changing it builds a new
                                    // HomeViewModel, because the old one holds a
                                    // feed fetched from somewhere else.
                                    viewModel = viewModel(key = "home-$baseUrl") {
                                        HomeViewModel(container.videoRepository)
                                    },
                                    mediaBaseUrl = baseUrl,
                                    onOpenSettings = { tab = Tab.Settings },
                                    onOpenVideo = { watching = WatchSession(it) },
                                    onOpenChannel = { route = Route.Channel(it) },
                                )

                                Tab.Subscriptions -> SubscriptionsScreen(
                                    viewModel = viewModel(key = "subs-$baseUrl") {
                                        SubscriptionsViewModel(container.videoRepository)
                                    },
                                    mediaBaseUrl = baseUrl,
                                    onOpenSettings = { tab = Tab.Settings },
                                    // The channel screen does not exist yet, so
                                    // pressing a row does nothing rather than
                                    // pretending. Drawn anyway: the list is the
                                    // answer to "who do I follow", which is most of
                                    // what this tab is for.
                                    onOpenChannel = { route = Route.Channel(it) },
                                )

                                Tab.History -> HistoryScreen(
                                    viewModel = viewModel(key = "history-$baseUrl") {
                                        HistoryViewModel(container.videoRepository)
                                    },
                                    mediaBaseUrl = baseUrl,
                                    onOpenSettings = { tab = Tab.Settings },
                                    onOpenVideo = { watching = WatchSession(it) },
                                )

                                Tab.Settings -> SettingsScreen(
                                    baseUrl = baseUrl,
                                    language = chosen,
                                    feedMix = feedMix,
                                    onOpenServer = { route = Route.Setup },
                                    onOpenSaved = { route = Route.Saved },
                                    onChangeMix = { next ->
                                        // Drawn immediately, sent after: a
                                        // slider that waits for a round trip
                                        // before moving is one that feels stuck.
                                        feedMix = next
                                        scope.launch {
                                            runCatching {
                                                container.videoRepository.saveFeedMix(next)
                                            }
                                        }
                                    },
                                    onPickLanguage = { picked ->
                                        language = picked
                                        scope.launch {
                                            container.serverRepository.setLanguage(picked.code)
                                        }
                                    },
                                )
                            }
                        }

                    is Route.Saved -> SavedScreen(
                    viewModel = viewModel(key = "saved-$baseUrl") {
                        SavedViewModel(container.videoRepository)
                    },
                    mediaBaseUrl = baseUrl,
                    onBack = { route = Route.Home },
                    onOpenSettings = { route = Route.Setup },
                    onOpenVideo = { watching = WatchSession(it) },
                    onOpenChannel = { route = Route.Channel(it) },
                )

                is Route.Search -> SearchScreen(
                        viewModel = viewModel(key = "search-$baseUrl") {
                            SearchViewModel(container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = Route.Home },
                        onOpenVideo = { watching = WatchSession(it) },
                    )

                    is Route.Channel -> ChannelScreen(
                        viewModel = viewModel(key = "channel-${current.channelId}") {
                            ChannelViewModel(current.channelId, container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = Route.Home },
                        onOpenSettings = { route = Route.Setup },
                        onOpenVideo = { watching = WatchSession(it) },
                    )
                }

                // The strip behind the system's clock, painted **once**.
            //
            // It was painted by each screen that happened to have a spacer for
            // it — three of them — while Channel, Saved and Setup only reserved
            // the space without filling it. That was invisible until the system
            // glyphs were told to draw dark, and then the clock vanished on
            // exactly those three. Measured: `#0F0F0F` under dark ink.
            //
            // Drawn last so it covers, and sized from `WindowInsets.statusBars`,
            // which is zero while the bar is hidden — so fullscreen gets no
            // white band without a condition saying so.
            Spacer(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Tokens.statusBar),
            )

            val session = watching
            // Not on the setup screen. Somebody typing an address is fixing the
            // connection this video came through, and a bar playing over that
            // form is in the way of the one thing that screen is for.
            val browsing = route is Route.Home || route is Route.Search ||
                route is Route.Channel || route is Route.Saved

            if (session != null && browsing) {
                // `remember` and a DisposableEffect, not `viewModel()`. That
                // helper stores a ViewModel in the *activity's* store, where it
                // outlives the screen entirely: every video opened would leave
                // another instance behind, each holding a live connection to the
                // playback service, and none of them would ever run `close()`.
                // This is only correct because the activity declares
                // `configChanges` for rotation and so is never recreated under
                // it.
                val watch = remember(session.videoId) {
                    WatchViewModel(
                        videoId = session.videoId,
                        startAtBeginning = session.startAtBeginning,
                        mediaBaseUrl = baseUrl,
                        videos = container.videoRepository,
                        streams = container.streamRepository,
                        narration = container.narrationRepository,
                        preferences = container.preferencesRepository,
                        onFinished = { next ->
                            trail.add(session.videoId)
                            watching = WatchSession(next, startAtBeginning = true)
                        },
                        playerFactory = container.playerFactory,
                    )
                }
                DisposableEffect(watch) { onDispose { watch.close() } }

                if (session.minimised) {
                    val playbackState by watch.state.collectAsStateWithLifecycle()
                    val playing = playbackState as? WatchState.Playing
                    MiniPlayer(
                        player = watch.player,
                        title = playing?.video?.title.orEmpty(),
                        channel = playing?.video?.channel?.name.orEmpty(),
                        progress = playing?.playback?.progress ?: 0f,
                        isPlaying = playing?.playback?.isPlaying == true,
                        onExpand = { watching = session.copy(minimised = false) },
                        onPlayPause = watch::playPause,
                        onClose = {
                            // Stop first, then let go. The disposal that follows
                            // only releases the connection.
                            watch.stop()
                            watching = null
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // It sits *on* the tab bar, not over it — the bar is
                            // how somebody leaves for another tab while this
                            // keeps playing. Search and the channel page have no
                            // tab bar, so there is nothing to clear there.
                            .padding(
                                bottom = WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding() +
                                    if (route is Route.Home) Size.topBar else 0.dp,
                            ),
                    )
                } else {
                    WatchLayer(onMinimise = { watching = session.copy(minimised = true) }) {
                        WatchScreen(
                            viewModel = watch,
                            mediaBaseUrl = baseUrl,
                            onBack = { watching = session.copy(minimised = true) },
                            onOpenVideo = { watching = WatchSession(it) },
                            // Advancing starts the next video at the beginning;
                            // choosing one resumes it. Two different acts, and
                            // the difference is what stops "next" dropping
                            // somebody into the middle of a track.
                            onAdvanceTo = {
                                trail.add(session.videoId)
                                watching = WatchSession(it, startAtBeginning = true)
                            },
                            onPlayPrevious = {
                                val previous = trail.removeLastOrNull()
                                // Resumed, not restarted: going back means
                                // returning to where you were.
                                if (previous != null) watching = WatchSession(previous)
                            },
                            hasPrevious = trail.isNotEmpty(),
                            onOpenChannel = {
                                // The video keeps playing, collapsed, so opening
                                // a channel from the watch screen does not end
                                // what somebody was listening to.
                                watching = session.copy(minimised = true)
                                route = Route.Channel(it)
                            },
                        )
                    }
                }
            }

        }
    }
    }
}
