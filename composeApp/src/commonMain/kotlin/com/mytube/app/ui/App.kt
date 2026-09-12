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
import com.mytube.app.ui.home.Chip
import com.mytube.app.ui.home.ChipRow
import com.mytube.app.ui.home.Size
import com.mytube.app.ui.home.HomeState
import com.mytube.app.ui.home.HomeViewModel
import com.mytube.app.ui.home.Space
import com.mytube.app.ui.shell.rememberLandingKnock
import com.mytube.app.ui.shell.AppShell
import com.mytube.app.ui.theme.Tokens
import com.mytube.app.ui.shell.GlassMenu
import com.mytube.app.ui.shell.dismissMenuOnOutsidePress
import com.mytube.app.ui.shell.LocalBackdrop
import com.mytube.app.ui.shell.LocalNativeGlass
import com.mytube.app.ui.shell.NativeGlassBridge
import com.mytube.app.ui.shell.NativeGlassRegistry
import com.mytube.app.ui.shell.LocalMiniPlayerShowing
import com.mytube.app.ui.shell.edgeBack
import kotlin.math.roundToInt
import com.mytube.app.ui.shell.rememberBarsVisible
import com.mytube.app.ui.shell.Tab
import com.mytube.app.ui.watch.WatchLayer
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.mytube.app.ui.watch.QueueItem
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
import com.mytube.app.ui.playlist.PlaylistNameAlert
import com.mytube.app.ui.playlist.PlaylistScreen
import com.mytube.app.ui.playlist.PlaylistViewModel
import com.mytube.app.ui.playlist.PlaylistsScreen
import com.mytube.app.ui.playlist.PlaylistsState
import com.mytube.app.ui.playlist.PlaylistsViewModel
import com.mytube.app.ui.playlist.SavePlaylistSheet
import com.mytube.app.ui.playlist.SavePlaylistViewModel
import com.mytube.app.ui.playlist.SaveTarget
import com.mytube.app.ui.saved.SavedScreen
import com.mytube.app.ui.saved.SavedViewModel
import com.mytube.app.ui.search.SEARCH_FIELD_ROW
import com.mytube.app.ui.search.SearchScreen
import com.mytube.app.ui.search.SearchViewModel
import com.mytube.app.ui.settings.LanguageScreen
import com.mytube.app.ui.settings.ProfileScreen
import com.mytube.app.ui.settings.SettingsScreen
import com.mytube.app.ui.settings.VoiceScreen
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
    /** The channels this household follows, reached from Settings. */
    data object Subscriptions : Route
    data class Playlist(val playlistId: String) : Route
    data object History : Route
    data object Profile : Route
    /** The Vietnamese voice's levels and name, opened from Settings. */
    data object Voice : Route
    /** Which language the app reads in, opened from Settings. */
    data object Language : Route
    data class Channel(val channelId: String) : Route
}

/**
 * The video being watched, and whether it is collapsed to the miniplayer.
 *
 * Not a route. A route is somewhere you are, and this is something that keeps
 * playing while you go elsewhere — modelling it as a destination is what made
 * leaving the screen the same act as stopping the video.
 */
/**
 * One more than the last, and nothing else is asked of it.
 *
 * Not a timestamp: two sessions opened in the same millisecond would share one,
 * and the whole point of this number is that two sittings never do. Not a UUID
 * either — this never leaves the process.
 */
private var sittings = 0L

private fun nextSittingId(): Long = ++sittings

private data class WatchSession(
    val videoId: String,
    /**
     * The sitting this video belongs to, and what the player is keyed on.
     *
     * A sitting outlives the video in it. Autoplay moves `videoId` on while the
     * same `WatchViewModel` keeps the same connection to the same player, which
     * is what lets a video advance with the screen off — building a new one is
     * `remember`, and `remember` is composition, and composition stops when the
     * app leaves the foreground.
     *
     * Generated once per `WatchSession(...)` and carried through `copy`, so
     * "open this video" starts a new sitting and "the last one finished" does
     * not.
     */
    val sittingId: Long = nextSittingId(),
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
    val queue: List<QueueItem> = emptyList(),
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
    is Route.Deciding, is Route.Home -> 0
    // One level in, like every other row in Settings. It was ground with Home,
    // on the reasoning that it is where the app starts before there is a
    // library — and the consequence was that leaving it slid the wrong way, the
    // one screen in the menu whose animation disagreed with its neighbours. A
    // fresh install now slides in from the right on first launch, which is what
    // arriving somewhere looks like.
    is Route.Setup -> 1
    is Route.Search -> 1
    is Route.Subscriptions -> 1
    /**
     * Two, with [Route.Playlist], and for the same reason.
     *
     * The shelf used to be reached from Settings and was ground-plus-one like
     * every other row there. It is a row on the playlists page now — equal
     * depth, and `forward` is `target >= initial`, so leaving it slid the wrong
     * way. Reported the day it moved.
     */
    is Route.Saved -> 2
    /**
     * Two, like [Route.Channel], and for that entry's reason.
     *
     * A playlist is reached *from* the playlists page, which is itself one level
     * in. At equal depths `forward` is `target >= initial`, so leaving one would
     * animate as another step inward.
     */
    is Route.Playlist -> 2
    /**
     * Two, and it is the only route that is.
     *
     * A channel is the one page reached *from* another page at depth 1 — from a
     * search result, from the saved shelf, from watch history — as well as from
     * Home. With it at 1 those pairs had equal depth, and `forward` is
     * `target >= initial`: leaving a channel for the history page it was opened
     * from counted as going *deeper*, so back slid the wrong way. Reported
     * exactly that way.
     *
     * Depth is what the direction is read from, so the fix belongs here rather
     * than in the transition: the channel genuinely is one level under whatever
     * listed it.
     */
    is Route.Channel -> 2
    // Opened from Settings, beside Saved and for the same reason it is there:
    // a shelf this device keeps, not somewhere you move between while browsing.
    is Route.History, is Route.Profile -> 1
    // Opened from Settings, which is a tab — so one level in, the same as the
    // saved shelf beside them in that menu.
    is Route.Voice, is Route.Language -> 1
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
fun App(
    container: AppContainer,
    /**
     * Whether the platform draws the tab bar rather than Compose.
     *
     * True only from `MainViewController` on iOS 26, where the bar is a SwiftUI
     * view carrying the system's Liquid Glass. `MainActivity` does not pass it,
     * so the Android shell is the one it always was — the parameter exists so
     * that is provable from one line rather than argued about.
     *
     * See [ShellBridge] for what crosses, and for why the Compose tree is *not*
     * split into one controller per tab the way JetBrains' guide does it.
     */
    /**
     * Whether a platform layer over this scene draws the glass on the player.
     *
     * True on iOS 26 alone. It used to be `nativeTabBar` and to mean two things
     * — a SwiftUI tab bar *and* the player's panes — and the two came apart: the
     * bar went back to Compose because its three siblings could never leave it,
     * while the player's controls have to stay platform-drawn because they sit
     * over a video Compose cannot sample.
     */
    nativeGlass: Boolean = false,
) {
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

    CompositionLocalProvider(
        LocalStrings provides chosen.strings,
        // The same flag, read where the glass is rather than passed to it. It is
        // one fact about the shell — whether a platform layer is drawing over
        // this scene — and the entry point is the only thing that knows it.
        LocalNativeGlass provides nativeGlass,
    ) {
        MytubeTheme {
            var route: Route by remember { mutableStateOf(Route.Deciding) }
            // Where a channel page was opened from, so back returns there.
            //
            // Every other page in this app is reached from exactly one place —
            // Saved and Language from Settings, search from the tab bar — so
            // "back" is Home for all of them and always right. A channel is
            // reached from a feed card, a search result, the saved shelf, watch
            // history and the watch screen, and sending all five back to Home
            // throws away the list somebody was reading.
            //
            // One level, deliberately: this is a *var*, not a stack, and the
            // second channel opened from inside a channel overwrites the first.
            // A stack would model a history nothing here creates — the only way
            // to that second channel is through a video, and opening a video
            // leaves this route standing rather than pushing onto it.
            var channelFrom: Route by remember { mutableStateOf(Route.Home) }
            // The one way to a channel, so the two lines never come apart. Five
            // call sites set the route directly and four of them would have
            // forgotten this one.
            val openChannel: (String) -> Unit = { id ->
                channelFrom = route
                route = Route.Channel(id)
            }
            var tab by remember { mutableStateOf(Tab.Home) }
            var baseUrl by remember { mutableStateOf("") }
            // Null when nothing is playing. Held above the routes so that
            // changing tab, or stepping into settings, does not take the video
            // with it.
            var watching: WatchSession? by remember { mutableStateOf(null) }
            // What the save sheet was opened for, or null when it is shut.
            //
            // Hoisted here because six screens open the same sheet — Home,
            // history, a channel, the saved shelf, search and the watch layer —
            // and because a `GlassSheet` is an ordinary child of the root `Box`
            // rather than a popup: where it is written is where it sits, so it
            // has to be written last, once, above everything.
            var savingTarget: SaveTarget? by remember { mutableStateOf(null) }
            // Kept while the sheet plays its exit, so the rows do not vanish
            // mid-slide. `visible` is what the sheet animates on; this is what
            // it draws.
            var sheetOpen by remember { mutableStateOf(false) }
            // Who wants to hear what the sheet applied, if anybody does.
            //
            // Only the watch screen does: its Save pill is lit from
            // `video.saved`, so it has to be told. A card's menu row reads
            // "Lưu vào playlist" whatever the answer, and has nothing to redraw.
            var savedSink: ((Boolean) -> Unit)? by remember { mutableStateOf(null) }
            val openSaveSheet: (SaveTarget) -> Unit = { target ->
                savingTarget = target
                sheetOpen = true
                savedSink = null
            }
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
            // The subscriptions page's scroll, for the tabs' reason: it is a
            // route, so it is not composed while anything else is, and a state
            // remembered inside it would die there.
            val subscriptionsScroll = rememberLazyListState()
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
            // Hoisted out of the route, because two screens act on one list: the
            // page shows it, and a playlist deleted from *inside* a collection
            // has to leave it. Held in the activity's store by `viewModel()`, it
            // outlives the route — so without being told, the deleted row stayed
            // on the page, opened nothing, and was gone only after a restart.
            // Reported exactly that way.
            val playlists = viewModel(key = "playlists-$baseUrl-$profileId") {
                PlaylistsViewModel(container.videoRepository)
            }
            LaunchedEffect(baseUrl) {
                if (baseUrl.isNotBlank()) {
                    // Guarded. An unguarded throw here cancels the effect and
                    // takes `profileId` with it — so a server that was asleep
                    // for the two seconds after launch left the household with
                    // no members and an avatar that did nothing, for the rest of
                    // the run.
                    runCatching { container.serverRepository.profiles() }
                        .onSuccess { profiles = it }
                    profileId = runCatching { container.serverRepository.profileId() }
                        .getOrDefault(profileId)
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
            // The glass over the picture, pushed to the same platform layer
                // that draws the tab bar.
                //
                // Watched here rather than in `GlassPane` because the platform
                // wants the whole collection at once: several panes appear and
                // disappear together when the controls fade, and pushing one at
                // a time would send the layer through states no frame of the app
                // is ever in.
                val panes = NativeGlassRegistry.panes.values.sortedBy { it.id }
            LaunchedEffect(panes) { NativeGlassBridge.onPanes?.invoke(panes) }

            BackHandler(backable) {
                val session = watching
                when {
                    // Expanded video: collapse it rather than close it. The
                    // sound carries on, which is what the drag down does too.
                    session != null && !session.minimised ->
                        watching = session.copy(minimised = true)

                    // A channel, which is reached from five different places.
                    route is Route.Channel -> route = channelFrom

                    // A playlist is reached from exactly one page, so unlike a
                    // channel it needs nothing remembered.
                    // A collection is opened from the Playlists *tab*, so
                    // leaving it goes back to that tab rather than to a route
                    // that no longer exists.
                    route is Route.Playlist || route is Route.Saved -> {
                        route = Route.Home
                        tab = Tab.Playlists
                    }

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
            // The app's own page, recorded once so every floating surface can
            // sample it: the two bars, the chip row, the miniplayer and the
            // sheets. Owned here because the miniplayer is drawn beside the
            // shell rather than inside it, so one created in the shell would not
            // reach it.
            //
            // The background is drawn *into* the backdrop before the content.
            // Without it the recording has transparent pixels wherever a screen
            // does not paint, and the glass shows them as holes — which is the
            // first thing the library's own guide warns about.
            val backdrop = rememberLayerBackdrop {
                drawRect(Tokens.bg)
                drawContent()
            }

            CompositionLocalProvider(
                LocalMiniPlayerShowing provides (watching?.minimised == true),
                LocalBackdrop provides backdrop,
            ) {
            // The one place a touch can be seen before anything acts on it, which
            // is what closing a menu without swallowing the gesture needs.
            Box(Modifier.fillMaxSize().dismissMenuOnOutsidePress()) {
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
                        // A way out only once there is a library to go back to.
                        // On a fresh install this screen *is* the app, and an
                        // arrow there would lead nowhere.
                        onBack = if (baseUrl.isEmpty()) null else ({ route = Route.Home }),
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
                    ) {
                            when (tab) {
                                Tab.Home -> HomeScreen(
                                    listState = tabScroll.getValue(Tab.Home),
                                    viewModel = home,
                                    mediaBaseUrl = baseUrl,
                                    onOpenSettings = { tab = Tab.Settings },
                                    onOpenVideo = { watching = WatchSession(it) },
                                    onOpenChannel = openChannel,
                                    onSaveToPlaylist = {
                                        openSaveSheet(SaveTarget(it.id, saved = it.saved))
                                    },
                                )

                                // Collections are a tab now, where subscriptions
                                // used to be. Both are lists somebody scans for
                                // one name; the difference is how often, and a
                                // household reaches for its own playlists far
                                // more than for the list of who it follows —
                                // which is why that one moved into Settings.
                                //
                                // `onBack = null`: a tab is not reached from
                                // anywhere, so an arrow on it would lead
                                // nowhere. Same rule as the setup screen's.
                                Tab.Playlists -> PlaylistsScreen(
                                    viewModel = playlists,
                                    mediaBaseUrl = baseUrl,
                                    listState = tabScroll.getValue(Tab.Playlists),
                                    onBack = null,
                                    onOpenSettings = { route = Route.Setup },
                                    onOpenSaved = { route = Route.Saved },
                                    onOpenPlaylist = { route = Route.Playlist(it) },
                                )

                                Tab.Settings -> SettingsScreen(
                                    baseUrl = baseUrl,
                                    language = chosen,
                                    feedMix = feedMix,
                                    onOpenServer = { route = Route.Setup },
                                    onOpenProfile = { route = Route.Profile },
                                    onOpenHistory = { route = Route.History },
                                    onOpenSubscriptions = { route = Route.Subscriptions },
                                    profileName = currentProfile?.name.orEmpty(),
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
                                    onOpenVoice = { route = Route.Voice },
                                    onOpenLanguage = { route = Route.Language },
                                )
                            }
                        }
                    }

                    is Route.Voice -> VoiceScreen(
                        voiceLevel = voiceLevel,
                        duckLevel = duckLevel,
                        voice = voice,
                        onVoiceLevel = { level ->
                            voiceLevel = level
                            scope.launch { container.preferencesRepository.setVoiceLevel(level) }
                        },
                        onDuckLevel = { level ->
                            duckLevel = level
                            scope.launch { container.preferencesRepository.setDuckLevel(level) }
                        },
                        onVoice = { name ->
                            voice = name
                            scope.launch {
                                runCatching { container.serverRepository.setTtsVoice(name) }
                            }
                        },
                        onBack = { route = Route.Home },
                    )

                    is Route.Language -> LanguageScreen(
                        language = chosen,
                        onPick = { picked ->
                            language = picked
                            scope.launch { container.serverRepository.setLanguage(picked.code) }
                        },
                        onBack = { route = Route.Home },
                    )

                    is Route.History -> HistoryScreen(
                        viewModel = viewModel(key = "history-$baseUrl-$profileId") {
                            HistoryViewModel(container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = Route.Home },
                        onOpenSettings = { route = Route.Setup },
                        onOpenVideo = { watching = WatchSession(it) },
                        onOpenChannel = openChannel,
                        onSaveToPlaylist = {
                            openSaveSheet(SaveTarget(it.id, saved = it.saved))
                        },
                    )

                    is Route.Profile -> {
                        // Asked again on arrival rather than trusting the fetch
                        // at launch.
                        //
                        // That one is unguarded and runs while the app is
                        // starting, so a server still waking up leaves the
                        // household with no members — which used to leave the
                        // avatar in the bar doing nothing for the rest of the
                        // run. A page cannot shrug like that: opening it is
                        // asking the question, so it asks the server too.
                        LaunchedEffect(baseUrl) {
                            runCatching { container.serverRepository.profiles() }
                                .onSuccess { profiles = it }
                        }
                        ProfileScreen(
                            profiles = profiles,
                            currentId = currentProfile?.id.orEmpty(),
                            onPick = { picked ->
                                if (picked.id != profileId) {
                                    profileId = picked.id
                                    // Everything playing belongs to the person
                                    // who was watching. Their history, their
                                    // rail, their saved shelf — leaving the
                                    // video up would be one member's evening
                                    // carried into another's.
                                    watching = null
                                    trail.clear()
                                    tab = Tab.Home
                                    scope.launch {
                                        container.serverRepository.setProfileId(picked.id)
                                    }
                                }
                                route = Route.Home
                            },
                            onBack = { route = Route.Home },
                        )
                    }

                    is Route.Subscriptions -> SubscriptionsScreen(
                        listState = subscriptionsScroll,
                        viewModel = viewModel(key = "subs-$baseUrl-$profileId") {
                            SubscriptionsViewModel(container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = Route.Home },
                        onOpenSettings = { route = Route.Setup },
                        onOpenChannel = openChannel,
                    )

                    is Route.Playlist -> PlaylistScreen(
                        viewModel = viewModel(key = "playlist-${current.playlistId}") {
                            PlaylistViewModel(current.playlistId, container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = Route.Home; tab = Tab.Playlists },
                        onOpenSettings = { route = Route.Setup },
                        // The whole page becomes the queue, so next and autoplay
                        // stay inside the playlist. `WatchSession.queue` already
                        // carries this for the channel page; nothing in the
                        // player had to change.
                        onOpenVideo = { id, queue -> watching = WatchSession(id, queue = queue) },
                        onOpenChannel = openChannel,
                        onDeleted = { deleted ->
                            // Told rather than refetched: the page knows which
                            // row went, and asking the server again would draw
                            // the old list for as long as that round trip takes.
                            playlists.forget(deleted)
                            route = Route.Home
                            tab = Tab.Playlists
                        },
                    )

                    is Route.Saved -> SavedScreen(
                    viewModel = viewModel(key = "saved-$baseUrl-$profileId") {
                        SavedViewModel(container.videoRepository)
                    },
                    mediaBaseUrl = baseUrl,
                    // The shelf is the first row on the playlists tab now, so
                    // that is where leaving it returns to — the rule every other
                    // page here follows: back goes to whatever listed you.
                    onBack = { route = Route.Home; tab = Tab.Playlists },
                    onOpenSettings = { route = Route.Setup },
                    onOpenVideo = { watching = WatchSession(it) },
                    onOpenChannel = openChannel,
                )

                is Route.Search -> SearchScreen(
                        viewModel = viewModel(key = "search-$baseUrl-$profileId") {
                            SearchViewModel(container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = Route.Home },
                        onOpenVideo = { watching = WatchSession(it) },
                        onOpenChannel = openChannel,
                        onSaveToPlaylist = openSaveSheet,
                    )

                    is Route.Channel -> ChannelScreen(
                        viewModel = viewModel(key = "channel-${current.channelId}") {
                            ChannelViewModel(current.channelId, container.videoRepository)
                        },
                        mediaBaseUrl = baseUrl,
                        onBack = { route = channelFrom },
                        onOpenSettings = { route = Route.Setup },
                        // The channel's own order comes with the video, so next
                        // stays inside the list that was being read.
                        onOpenVideo = { id, queue ->
                            watching = WatchSession(id, queue = queue)
                        },
                        onSaveToPlaylist = {
                            openSaveSheet(SaveTarget(it.id, saved = it.saved))
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
            // Everywhere but the setup form. Somebody typing an address is
            // fixing the connection this video came through, and a bar playing
            // over that form is in the way of the one thing that screen is for.
            // The settings details are not that: they are pages, and the sound
            // carrying on across them is the whole point of holding the session
            // above the routes.
            val browsing = route is Route.Home || route is Route.Search ||
                route is Route.Channel || route is Route.Saved ||
                route is Route.Subscriptions || route is Route.Playlist ||
                route is Route.Voice || route is Route.Language ||
                route is Route.History || route is Route.Profile

            if (session != null && browsing) {
                // `remember` and a DisposableEffect, not `viewModel()`. That
                // helper stores a ViewModel in the *activity's* store, where it
                // outlives the screen entirely: every video opened would leave
                // another instance behind, each holding a live connection to the
                // playback service, and none of them would ever run `close()`.
                // This is only correct because the activity declares
                // `configChanges` for rotation and so is never recreated under
                // it.
                val watch = remember(session.sittingId) {
                    WatchViewModel(
                        videoId = session.videoId,
                        startAtBeginning = session.startAtBeginning,
                        autoPlay = session.autoPlay,
                        mediaBaseUrl = baseUrl,
                        videos = container.videoRepository,
                        streams = container.streamRepository,
                        narration = container.narrationRepository,
                        preferences = container.preferencesRepository,
                        openedFrom = session.queue,
                        // Told after the fact: the ViewModel has already loaded
                        // the next video by the time this runs — see
                        // `advanceTo` — and this is the route catching up with
                        // it. `copy`, not a new session, or the key above would
                        // change and the player would be torn down and rebuilt
                        // mid-song.
                        onFinished = { next ->
                            trail.add(watching?.videoId ?: session.videoId)
                            watching = watching?.copy(
                                videoId = next,
                                startAtBeginning = true,
                            )
                        },
                        playerFactory = container.playerFactory,
                    )
                }
                DisposableEffect(watch) { onDispose { watch.close() } }

                // The session is the truth and the ViewModel follows it.
                //
                // Two things move `session.videoId`: pressing next or picking
                // from the rail, which happen while the app is on screen, and
                // autoplay, which may happen while it is not. `advanceTo`
                // checks the id first, so the second case — where the ViewModel
                // moved first and told the route afterwards — costs nothing
                // here.
                LaunchedEffect(session.videoId) {
                    watch.advanceTo(session.videoId, session.startAtBeginning)
                }

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
                val knock = rememberLandingKnock()

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
                        bottom = navigationInset + when {
                            route is Route.Home -> Size.topBar
                            // The search screen's field now owns the bottom of
                            // that screen, the same way the tab bar owns Home's.
                            // Without this the bar rests across the one control
                            // that screen exists for.
                            route is Route.Search -> SEARCH_FIELD_ROW
                            else -> 0.dp
                        },
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
                        onMinimise = {
                            // The one knock in the app, and it is an impact
                            // rather than a selection tick: the picture has been
                            // travelling under a finger for the whole gesture
                            // and this is it arriving. Here rather than on every
                            // route into `minimised = true` — the back gesture
                            // and opening a channel also collapse the video, and
                            // neither is a thing landing anywhere.
                            knock()
                            watching = session.copy(minimised = true)
                        },
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
                            // Picked from the rail: the same sitting, resumed
                            // rather than restarted.
                            onOpenVideo = {
                                trail.add(session.videoId)
                                watching = session.copy(
                                    videoId = it,
                                    startAtBeginning = false,
                                )
                            },
                            // Advancing starts the next video at the beginning;
                            // choosing one resumes it. Two different acts, and
                            // the difference is what stops "next" dropping
                            // somebody into the middle of a track.
                            // `copy`, so the sitting — and with it the player and
                            // its connection — survives moving between videos.
                            // A new `WatchSession` here would rebuild the
                            // ViewModel and tear the sound down between two
                            // tracks that are meant to run on.
                            onAdvanceTo = {
                                trail.add(session.videoId)
                                watching = session.copy(
                                    videoId = it,
                                    startAtBeginning = true,
                                )
                            },
                            onPlayPrevious = {
                                val previous = trail.removeLastOrNull()
                                // Resumed, not restarted: going back means
                                // returning to where you were.
                                if (previous != null) {
                                    watching = session.copy(
                                        videoId = previous,
                                        startAtBeginning = false,
                                    )
                                }
                            },
                            hasPrevious = trail.isNotEmpty(),
                            onOpenChannel = {
                                // The video keeps playing, collapsed, so opening
                                // a channel from the watch screen does not end
                                // what somebody was listening to.
                                watching = session.copy(minimised = true)
                                openChannel(it)
                            },
                            onSaveToPlaylist = { saved ->
                                openSaveSheet(SaveTarget(session.videoId, saved = saved))
                                // The pill redraws from what the sheet applied.
                                // `markSaved` sends nothing: the request has
                                // already happened by then, and two writers of
                                // one fact is how they come to disagree.
                                savedSink = watch::markSaved
                            },
                        )
                    }
                    }
                }

            }

            // The save sheet, drawn last and therefore on top.
            //
            // A **sibling** of the watch session's block, not a child of it.
            // It lived inside `if (session != null && browsing)` and that is
            // the whole of the fault reported from the phone: with nothing
            // playing, a card's menu set the target and the sheet had no
            // parent to be drawn by, so the press did nothing — and opening a
            // video afterwards composed that branch with the target still set,
            // so the sheet rose on its own. Six screens open this one sheet,
            // and none of them is about a video that happens to be playing.
            //
            // Keyed on the video it was opened for, so opening it from a
            // second card asks the server again rather than showing the
            // first card's ticks. It stays composed while it shuts — the
            // target is held until the exit has played, or the rows would
            // vanish mid-slide.
            // The overflow menus, drawn here for the reason the sheets are: a
            // popup cannot sample a backdrop and a child of the recording cannot
            // either. Before the sheet, so a menu row that opens one is covered
            // by what it opened rather than left standing over it.
            GlassMenu()

            // Naming a new collection, drawn here rather than by the tab that
            // opens it. A tab is composed inside `AppShell`'s recording, so an
            // alert of sampled glass there samples the layer it is drawn into
            // and the app dies in Skia's image-filter bounds walk. The screen's
            // own `glassSource` is a no-op under a shell, which is why the two
            // boxes it used to wrap this in did not help.
            val playlistsState by playlists.state.collectAsStateWithLifecycle()
            val playlistStrings = LocalStrings.current
            PlaylistNameAlert(
                visible = (playlistsState as? PlaylistsState.Ready)?.creating == true,
                title = playlistStrings.newPlaylist,
                name = (playlistsState as? PlaylistsState.Ready)?.newName.orEmpty(),
                backdrop = backdrop,
                confirmLabel = playlistStrings.createPlaylist,
                onNameChanged = playlists::nameChanged,
                onDismiss = playlists::cancelCreating,
                onConfirm = playlists::create,
            )

            val savingFor = savingTarget
            if (savingFor != null) {
                SavePlaylistSheet(
                    viewModel = remember(savingFor) {
                        SavePlaylistViewModel(savingFor, container.videoRepository)
                    },
                    visible = sheetOpen,
                    mediaBaseUrl = baseUrl,
                    backdrop = backdrop,
                    onDismiss = { sheetOpen = false },
                    onSaved = { saved ->
                        savedSink?.invoke(saved)
                        sheetOpen = false
                    },
                )
            }
            }

        }
    }
    }
}
