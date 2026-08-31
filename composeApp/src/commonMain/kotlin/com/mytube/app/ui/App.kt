package com.mytube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mytube.app.domain.model.DEFAULT_DUCK_LEVEL
import com.mytube.app.domain.model.DEFAULT_VOICE_LEVEL
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.watch.MiniPlayer
import com.mytube.app.ui.watch.WatchState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mytube.app.AppContainer
import com.mytube.app.ui.home.HomeScreen
import com.mytube.app.ui.home.ChipRow
import com.mytube.app.ui.home.HomeState
import com.mytube.app.ui.home.HomeViewModel
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.shell.AppShell
import com.mytube.app.ui.shell.LocalHaze
import com.mytube.app.ui.shell.LocalMiniPlayerShowing
import com.mytube.app.ui.shell.edgeBack
import kotlin.math.roundToInt
import com.mytube.app.ui.shell.rememberBarsVisible
import com.mytube.app.ui.shell.ProfileSheet
import com.mytube.app.ui.shell.Tab
import com.mytube.app.ui.watch.WatchLayer
import dev.chrisbanes.haze.rememberHazeState
import com.mytube.app.ui.watch.WatchScreen
import com.mytube.app.ui.watch.WatchViewModel
import com.mytube.app.ui.settings.ServerSetupScreen
import com.mytube.app.ui.settings.ServerSetupViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.mytube.app.ui.i18n.LocalStrings
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mytube.app.ui.channel.ChannelScreen
import com.mytube.app.ui.channel.ChannelViewModel
import com.mytube.app.ui.history.HistoryScreen
import com.mytube.app.ui.history.HistoryViewModel
import com.mytube.app.ui.i18n.Language
import com.mytube.app.ui.i18n.deviceLanguage
import com.mytube.app.domain.model.Profile
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
    /**
     * Whether opening this session should start the sound.
     *
     * True everywhere except the video restored from the last run of the app —
     * see `WatchViewModel.autoPlay`.
     */
    val autoPlay: Boolean = true,
    /**
     * The ordered list this video was opened from, or empty.
     *
     * Carried through the whole run of videos rather than looked up again:
     * pressing next builds the following session from this one, so a channel
     * page sorted by Popular stays in that order for as long as it is being
     * played through. The web app carries the same thing in the URL; here there
     * is no URL, and the session is the equivalent.
     */
    val queue: List<String> = emptyList(),
)

/**
 * How long a screen takes to arrive, in milliseconds.
 *
 * 260, inside the 150–300ms band where movement is read as movement rather than
 * as a wait. Below it the slide is a flicker that says nothing; above it the app
 * feels like it is thinking.
 */
private const val ROUTE_MILLIS = 260

/**
 * How deep a route sits, which is all the transition needs to know.
 *
 * Home is the ground. Everything opened from it is one level in, so it slides in
 * from the right and back out to the right — the direction every phone uses to
 * mean "this is on top of what you were looking at". Setup is the ground too:
 * it is where the app starts before there is a library, not somewhere you step
 * into from one.
 */
private fun depth(route: Route): Int = when (route) {
    is Route.Deciding, is Route.Setup, is Route.Home -> 0
    is Route.Search, is Route.Saved, is Route.Channel -> 1
}

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
            // One scroll position per tab, owned here because a tab that is not
            // showing is not composed — and a `rememberLazyListState` inside a
            // screen dies with it, which is why every trip to another tab and
            // back put the feed at the top again.
            val tabScroll = Tab.entries.associateWith { rememberLazyListState() }
            // How far the shell's bars have slid away, animated here rather than
            // inside the shell.
            //
            // The miniplayer is a *sibling* of the shell, not a child of it, and
            // it sits on the tab bar — so when the bar leaves, the bar's height
            // is no longer there to sit on and the player has to come down with
            // it. Animating this inside AppShell left the two disagreeing: the
            // bar slid away and the miniplayer stayed where the bar had been,
            // floating in the middle of the feed.
            //
            // 220ms: inside the 150–300ms band where motion reads as movement
            // rather than as a delay.
            val barsShowing = rememberBarsVisible(tabScroll.getValue(tab))
            val barsHidden by animateFloatAsState(
                targetValue = if (barsShowing) 0f else 1f,
                animationSpec = tween(220),
                label = "bars",
            )
            val scope = rememberCoroutineScope()
            // Null until the server answers. Read once for the process: it is
            // one setting for the household and nothing else in the app changes
            // it, so re-reading on every visit to Settings would be a request
            // for an answer already held.
            var feedMix: FeedMix? by remember { mutableStateOf(null) }
            // The household, and who this device is. Read once the address is
            // known; an empty list is the honest answer for a server that is not
            // answering, and for a house of one there is nothing to pick.
            // How the Vietnamese voice sounds. Held here rather than inside the
            // watch screen because Settings is where it is now edited and the
            // watch screen is where it takes effect — two tabs apart, so the
            // value has to live above both of them.
            //
            // The two levels are this device's; the voice is the household's and
            // lives on the server.
            var voiceLevel by remember { mutableStateOf(DEFAULT_VOICE_LEVEL) }
            var duckLevel by remember { mutableStateOf(DEFAULT_DUCK_LEVEL) }
            var voice by remember { mutableStateOf("") }
            // What was open when the app was last closed, put back as the
            // miniplayer.
            //
            // Restored **stopped**, and only once: `Unit` rather than `baseUrl`,
            // because this must not reach back for a video every time the
            // address is re-read — somebody who closed the bar deliberately
            // would find it back a moment later.
            LaunchedEffect(Unit) {
                val last = container.preferencesRepository.lastVideoId()
                if (last.isNotEmpty()) {
                    watching = WatchSession(last, minimised = true, autoPlay = false)
                }
            }
            // Written whenever what is open changes, and cleared when the
            // miniplayer's X is pressed. Closing is a statement — "I am done
            // with this" — and an app that puts it back on the next launch has
            // not listened.
            LaunchedEffect(watching?.videoId) {
                runCatching {
                    container.preferencesRepository
                        .setLastVideoId(watching?.videoId.orEmpty())
                }
            }
            LaunchedEffect(baseUrl) {
                voiceLevel = container.preferencesRepository.voiceLevel()
                duckLevel = container.preferencesRepository.duckLevel()
                if (baseUrl.isNotBlank()) voice = container.serverRepository.ttsVoice()
            }
            var profiles: List<Profile> by remember { mutableStateOf(emptyList()) }
            var profileId by remember { mutableStateOf("") }
            var profilesOpen by remember { mutableStateOf(false) }
            LaunchedEffect(baseUrl) {
                if (baseUrl.isNotBlank()) {
                    profiles = container.serverRepository.profiles()
                    profileId = container.serverRepository.profileId()
                }
            }
            // Who this device actually is, which is not always who it has
            // *chosen* to be.
            //
            // Nobody has chosen on a fresh install, so `profileId` is empty —
            // and empty must stay empty on the wire, because the gateway falls
            // back to its own default when `X-User-Id` is absent and that
            // fallback is what makes a new install work at all. But it means the
            // avatar had no name to draw and sat as an empty grey disc in the
            // most-looked-at corner of the app, for ever.
            //
            // The fallback here is not a guess: `router.go` answers an
            // unidentified request as `devUserID`, and `profiles.go` lists that
            // account first. The first profile *is* who the server thinks this
            // device is.
            val currentProfile = profiles.firstOrNull { it.id == profileId }
                ?: profiles.firstOrNull()
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
            // Every scrolling screen has to leave room for the miniplayer, and
            // it is an ambient fact rather than something to thread through
            // seven signatures. See `tabContentPadding`.
            // The blur every floating surface reads: the two bars, the feed's
            // chip row and the miniplayer. Owned here because the miniplayer is
            // drawn beside the shell rather than inside it, so a state created
            // in the shell would not reach it.
            val haze = rememberHazeState()

            CompositionLocalProvider(
                LocalMiniPlayerShowing provides (watching?.minimised == true),
                LocalHaze provides haze,
            ) {
            Box(Modifier.fillMaxSize()) {
                // Screens arrive and leave the way they do on the platforms this
                // runs on: a page pushed on slides in from the right over the one
                // it covers, and the one underneath drifts a little to the left
                // rather than sitting still — the parallax that says the two are
                // stacked. Going back reverses it.
                //
                // There was no transition at all before this: the whole window
                // was replaced between frames, which reads as the app having
                // jumped rather than moved, and leaves nothing to say *where*
                // the screen that was there has gone.
                //
                // `depth` rather than a boolean about which route is which: the
                // direction is a fact about the pair, and asking "is this deeper
                // than that" is one comparison instead of a matrix that grows
                // with every route added.
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val forward = depth(targetState) >= depth(initialState)
                        val enter = slideInHorizontally(tween(ROUTE_MILLIS)) { width ->
                            if (forward) width else -width / 4
                        } + fadeIn(tween(ROUTE_MILLIS))
                        val exit = slideOutHorizontally(tween(ROUTE_MILLIS)) { width ->
                            if (forward) -width / 4 else width
                        } + fadeOut(tween(ROUTE_MILLIS))
                        enter togetherWith exit
                    },
                    label = "route",
                ) { current ->
                // Only the screens that *have* a back. On Home the strip would
                // be a gesture that does nothing, which is worse than none: a
                // reader who finds it once expects it everywhere.
                Box(
                    if (depth(current) > 0) {
                        Modifier.fillMaxSize().edgeBack { route = Route.Home }
                    } else {
                        Modifier.fillMaxSize()
                    },
                ) {
                when (current) {
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
                    is Route.Home -> {
                    // Hoisted out of the HomeScreen call so the shell can draw
                    // this tab's chips inside the top bar. Same key, same
                    // lifetime — `viewModel()` returns the one instance either
                    // way, so nothing about the feed changed by moving where it
                    // is asked for.
                    val home = viewModel(key = "home-$baseUrl-$profileId") {
                        HomeViewModel(container.videoRepository)
                    }
                    val homeState by home.state.collectAsStateWithLifecycle()

                    AppShell(
                        current = tab,
                        barsHidden = barsHidden,
                        // Only the Home tab has anything to pin under the bar.
                        // The others pass nothing and the bar is its ordinary
                        // height, which is why the shell measures it rather than
                        // assuming.
                        underTopBar = {
                            val ready = homeState as? HomeState.Ready
                            if (tab == Tab.Home && ready != null) {
                                ChipRow(
                                    chips = ready.chips,
                                    selected = ready.selected,
                                    onSelect = home::select,
                                    modifier = Modifier.padding(bottom = Space.md),
                                )
                            }
                        },
                        onSelect = { picked ->
                            // Pressing the tab you are already on goes back to
                            // the top. Every phone app does this, and without it
                            // the only way back up a long feed is to swipe until
                            // your thumb aches. Deliberately *not* a refresh:
                            // pull-to-refresh already means that, and one
                            // gesture must not mean two things.
                            if (picked == tab) {
                                scope.launch {
                                    tabScroll.getValue(picked).animateScrollToItem(0)
                                }
                            }
                            tab = picked
                        },
                        onSearch = { route = Route.Search },
                        // Never passed at all before this: AppShell had the
                        // parameter and a default, and a default is exactly what
                        // hides a missing argument.
                        profileInitial = currentProfile?.name.orEmpty(),
                        onOpenProfiles = { if (profiles.size > 1) profilesOpen = true },
                    ) {
                            when (tab) {
                                Tab.Home -> HomeScreen(
                                    listState = tabScroll.getValue(Tab.Home),
                                    viewModel = home,
                                    mediaBaseUrl = baseUrl,
                                    onOpenSettings = { tab = Tab.Settings },
                                    onOpenVideo = { watching = WatchSession(it) },
                                    onOpenChannel = { route = Route.Channel(it) },
                                )

                                Tab.Subscriptions -> SubscriptionsScreen(
                                    listState = tabScroll.getValue(Tab.Subscriptions),
                                    viewModel = viewModel(key = "subs-$baseUrl-$profileId") {
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
                                    listState = tabScroll.getValue(Tab.History),
                                    viewModel = viewModel(key = "history-$baseUrl-$profileId") {
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
                                    voiceLevel = voiceLevel,
                                    duckLevel = duckLevel,
                                    voice = voice,
                                    // Drawn immediately, written after — the
                                    // same rule the feed mix follows. A slider
                                    // that waits for a write before it moves is
                                    // one somebody drags twice.
                                    onVoiceLevel = { level ->
                                        voiceLevel = level
                                        scope.launch {
                                            runCatching {
                                                container.preferencesRepository
                                                    .setVoiceLevel(level)
                                            }
                                        }
                                    },
                                    onDuckLevel = { level ->
                                        duckLevel = level
                                        scope.launch {
                                            runCatching {
                                                container.preferencesRepository
                                                    .setDuckLevel(level)
                                            }
                                        }
                                    },
                                    onVoice = { name ->
                                        voice = name
                                        scope.launch {
                                            runCatching {
                                                container.serverRepository.setTtsVoice(name)
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
                    }

                    is Route.Saved -> SavedScreen(
                    viewModel = viewModel(key = "saved-$baseUrl-$profileId") {
                        SavedViewModel(container.videoRepository)
                    },
                    mediaBaseUrl = baseUrl,
                    onBack = { route = Route.Home },
                    onOpenSettings = { route = Route.Setup },
                    onOpenVideo = { watching = WatchSession(it) },
                    onOpenChannel = { route = Route.Channel(it) },
                )

                is Route.Search -> SearchScreen(
                        viewModel = viewModel(key = "search-$baseUrl-$profileId") {
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
                        // The channel's own order comes with the video, so next
                        // stays inside the list that was being read.
                        onOpenVideo = { id, queue ->
                            watching = WatchSession(id, queue = queue)
                        },
                    )
                }
                }
                }

            // The chrome the miniplayer sits on, in pixels. Read once: the drag
            // gesture and the bar itself must be built from the same numbers, or
            // the video lands somewhere the bar is not.
            val density = LocalDensity.current
            val navigationInset =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val navigationInsetPx = with(density) { navigationInset.toPx() }
            val statusInsetPx = with(density) {
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
            }
            val tabBarPx = with(density) { Size.topBar.toPx() }
            val miniPlayerPx = with(density) { Size.miniPlayer.toPx() }
            // Reserved only where there is a tab bar to reserve for: search and
            // the channel page have none.
            val tabBarReservedPx = if (route is Route.Home) tabBarPx else 0f
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
                        autoPlay = session.autoPlay,
                        mediaBaseUrl = baseUrl,
                        videos = container.videoRepository,
                        streams = container.streamRepository,
                        narration = container.narrationRepository,
                        preferences = container.preferencesRepository,
                        queue = session.queue,
                        onFinished = { next ->
                            trail.add(session.videoId)
                            watching = WatchSession(
                                next,
                                startAtBeginning = true,
                                queue = session.queue,
                            )
                        },
                        playerFactory = container.playerFactory,
                    )
                }
                DisposableEffect(watch) { onDispose { watch.close() } }

                // Settings is a tab, so the sliders can be moved while this is
                // still playing in the miniplayer. Without this the player keeps
                // the levels it was handed when the video opened and the change
                // appears to do nothing until the next one.
                LaunchedEffect(watch, voiceLevel, duckLevel) {
                    watch.applyNarrationLevels(voiceLevel, duckLevel)
                }

                // How far the drag toward the bar has got, reported by the
                // watch layer. It lives here because the bar the picture is
                // travelling to is drawn here — only this scope knows where that
                // bar lands, which it already computes as `landingFromBottomPx`.
                //
                // Keyed so it cannot survive into a state where it is a lie: a
                // new video starts at zero, and so does a session that has just
                // collapsed.
                var dragProgress by remember(session.videoId, session.minimised) {
                    mutableFloatStateOf(0f)
                }

                val playbackState by watch.state.collectAsStateWithLifecycle()
                val playing = playbackState as? WatchState.Playing

                // One modifier, two call sites — the bar drawn under the drag
                // and the bar left behind by it. They have to land on the same
                // pixel: `landingFromBottomPx` below is built from these same
                // three terms, so a placement that differed between them would
                // put the picture down somewhere the bar is not.
                val miniPlayerModifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Follows the tab bar down as it leaves. The padding
                    // below reserves the bar's height; when the bar is
                    // gone that reservation is a gap, and the player was
                    // left hanging in the middle of the feed.
                    //
                    // `offset`, not `graphicsLayer { translationY }`.
                    //
                    // Haze samples from the node's **layout** position and knows
                    // nothing about a draw-time transform, so a translated
                    // surface drew its glass at the place it would have been —
                    // which on a moving miniplayer meant no background at all,
                    // and the tab bar showing straight through it. `offset`
                    // moves the node at placement, so the position Haze reads is
                    // the position it is drawn at.
                    .offset {
                        IntOffset(
                            x = 0,
                            // The tab bar's *whole* height, inset
                            // included — that is what slides away, and
                            // matching only the 56dp row left the player
                            // hanging 34dp short of the screen edge.
                            y = if (route is Route.Home) {
                                (barsHidden * (tabBarPx + navigationInsetPx))
                                    .roundToInt()
                            } else {
                                0
                            },
                        )
                    }
                    // It sits *on* the tab bar, not over it — the bar is
                    // how somebody leaves for another tab while this
                    // keeps playing. Search and the channel page have no
                    // tab bar, so there is nothing to clear there.
                    //
                    // The navigation inset **and** the tab bar's row.
                    //
                    // Both, because the tab bar's own row sits *above*
                    // the inset: it runs from 34dp to 90dp off the
                    // bottom of a phone with a home indicator. Resting
                    // the player at 56dp put it straight over that row's
                    // icons, which is why they vanished and the bar
                    // looked covered. It rests on top of the whole
                    // thing, and slides down by the whole thing.
                    .padding(
                        bottom = navigationInset +
                            if (route is Route.Home) Size.topBar else 0.dp,
                    )

                if (session.minimised) {
                    MiniPlayer(
                        player = watch.player,
                        title = playing?.video?.title.orEmpty(),
                        channel = playing?.video?.channel?.name.orEmpty(),
                        progress = playing?.playback?.progress ?: 0f,
                        isPlaying = playing?.playback?.isPlaying == true,
                        onExpand = { watching = session.copy(minimised = false) },
                        onPlayPause = watch::playPause,
                        // Nothing while the tab bar is under it; the full
                        // navigation inset once that bar has gone, so the glass
                        // still reaches the bottom of the screen.
                        bottomInset = navigationInset * barsHidden,
                        onClose = {
                            // Stop first, then let go. The disposal that follows
                            // only releases the connection.
                            watch.stop()
                            watching = null
                        },
                        showSurface = true,
                        modifier = miniPlayerModifier,
                    )
                } else {
                    // Started at `false` and flipped on the first frame, which
                    // is what makes the rise actually play: an AnimatedVisibility
                    // composed already visible has nothing to animate *from*, so
                    // the player would appear whole and instantly — the very
                    // thing this exists to replace.
                    //
                    // This is the other half of "tap a video and it plays": the
                    // card is pressed, the player rises from the bottom of the
                    // screen over the feed, and the feed stays where it was
                    // underneath. Reversed, it is the same movement the drag
                    // gesture makes by hand.
                    val rise = remember { MutableTransitionState(false) }
                    rise.targetState = true

                    // The bar the picture is being dragged into, drawn from the
                    // first pixel of the gesture and fading in with it.
                    //
                    // Before this the bar did not exist until the drag had
                    // committed: what a finger uncovered was the watch layer's
                    // own ground losing its blur, and the miniplayer appeared at
                    // the end from nowhere. Reported as "kéo xuống thì nó không
                    // show background của mini player" — accurately, because
                    // there was no miniplayer to show a background for.
                    //
                    // Drawn *before* the layer, so it is underneath it: the
                    // layer takes every touch while the drag is running, which
                    // is also why the buttons here cannot be pressed by accident
                    // on the way down. They are wired to the real callbacks
                    // anyway, so the two call sites stay identical.
                    //
                    // Composed only while the drag is live — at rest this would
                    // be a whole invisible bar laid out on every frame.
                    if (dragProgress > 0f) {
                        MiniPlayer(
                            player = watch.player,
                            title = playing?.video?.title.orEmpty(),
                            channel = playing?.video?.channel?.name.orEmpty(),
                            progress = playing?.playback?.progress ?: 0f,
                            isPlaying = playing?.playback?.isPlaying == true,
                            onExpand = { watching = session.copy(minimised = false) },
                            onPlayPause = watch::playPause,
                            bottomInset = navigationInset * barsHidden,
                            onClose = {
                                watch.stop()
                                watching = null
                            },
                            // The watch screen is holding the player's one
                            // surface, and the real picture is travelling down
                            // into this very box — see the flag's own comment.
                            showSurface = false,
                            // Linear across the whole gesture. Filling in early
                            // would leave the glass solid while the video is
                            // still twice the width of the box it is heading
                            // for, which is the moment the mismatch is most
                            // visible.
                            modifier = miniPlayerModifier
                                .graphicsLayer { alpha = dragProgress },
                        )
                    }

                    AnimatedVisibility(
                        visibleState = rise,
                        enter = slideInVertically(tween(ROUTE_MILLIS)) { it } +
                            fadeIn(tween(ROUTE_MILLIS)),
                        exit = slideOutVertically(tween(ROUTE_MILLIS)) { it } +
                            fadeOut(tween(ROUTE_MILLIS)),
                    ) {
                    WatchLayer(
                        onMinimise = { watching = session.copy(minimised = true) },
                        // Exactly where the miniplayer's own bar will be — the
                        // same three terms its padding and its translation are
                        // built from, so the picture arrives at the bar rather
                        // than past it.
                        landingFromBottomPx =
                            (navigationInsetPx + tabBarReservedPx) * (1f - barsHidden) +
                                miniPlayerPx +
                                navigationInsetPx * barsHidden,
                        topInsetPx = statusInsetPx,
                        onDragProgress = { dragProgress = it },
                    ) {
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
                                watching = WatchSession(
                                    it,
                                    startAtBeginning = true,
                                    queue = session.queue,
                                )
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

            // Last child of the Box, and that is load-bearing now.
            //
            // As a `ModalBottomSheet` this sat wherever it was written, because a
            // popup layer is always on top; drawn in the scene it is on top only
            // if it is drawn last. Written where it used to be, it opened
            // *underneath* every screen and the miniplayer.
            //
            // Always composed, told whether it is showing: an `if` around it
            // removes the node the exit animation would play on, so the sheet
            // would vanish rather than slide away.
            ProfileSheet(
                visible = profilesOpen,
                profiles = profiles,
                currentId = currentProfile?.id.orEmpty(),
                onDismiss = { profilesOpen = false },
                onPick = { picked ->
                    profilesOpen = false
                    if (picked.id != profileId) {
                        profileId = picked.id
                        // Everything playing belongs to the person who was
                        // watching. Their history, their rail, their saved
                        // shelf — leaving the video up would be one member's
                        // evening carried into another's.
                        watching = null
                        trail.clear()
                        tab = Tab.Home
                        scope.launch { container.serverRepository.setProfileId(picked.id) }
                    }
                },
            )
            }
            }

        }
    }
    }
}
