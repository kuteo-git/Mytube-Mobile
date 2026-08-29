package com.mytube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
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
import com.mytube.app.ui.history.HistoryScreen
import com.mytube.app.ui.history.HistoryViewModel
import com.mytube.app.ui.i18n.Language
import com.mytube.app.ui.i18n.deviceLanguage
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
}

/**
 * The video being watched, and whether it is collapsed to the miniplayer.
 *
 * Not a route. A route is somewhere you are, and this is something that keeps
 * playing while you go elsewhere — modelling it as a destination is what made
 * leaving the screen the same act as stopping the video.
 */
private data class WatchSession(val videoId: String, val minimised: Boolean = false)

/**
 * The root.
 *
 * Takes the container rather than reaching for a global: every dependency any
 * screen has arrives through this one argument, which is what makes the graph
 * something a reader can follow and a test can replace.
 */
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
            val scope = rememberCoroutineScope()

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
                is Route.Home -> Box(Modifier.fillMaxSize()) {
                    AppShell(current = tab, onSelect = { tab = it }) {
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
                                onOpenChannel = {},
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
                                onOpenServer = { route = Route.Setup },
                                onPickLanguage = { picked ->
                                    language = picked
                                    scope.launch {
                                        container.serverRepository.setLanguage(picked.code)
                                    }
                                },
                            )
                        }
                    }

                    val session = watching
                    if (session != null) {
                        // `remember` and a DisposableEffect, not `viewModel()`.
                        // That helper stores a ViewModel in the *activity's*
                        // store, where it outlives the screen entirely: every
                        // video opened would leave another instance behind, each
                        // holding a live connection to the playback service, and
                        // none of them would ever run `close()`. This is only
                        // correct because the activity declares `configChanges`
                        // for rotation and so is never recreated under it.
                        val watch = remember(session.videoId) {
                            WatchViewModel(
                                videoId = session.videoId,
                                mediaBaseUrl = baseUrl,
                                videos = container.videoRepository,
                                streams = container.streamRepository,
                                playerFactory = container.playerFactory,
                            )
                        }
                        DisposableEffect(watch) { onDispose { watch.close() } }

                        if (session.minimised) {
                            val state by watch.state.collectAsStateWithLifecycle()
                            val playing = state as? WatchState.Playing
                            MiniPlayer(
                                player = watch.player,
                                title = playing?.video?.title.orEmpty(),
                                channel = playing?.video?.channel?.name.orEmpty(),
                                progress = playing?.playback?.progress ?: 0f,
                                isPlaying = playing?.playback?.isPlaying == true,
                                onExpand = { watching = session.copy(minimised = false) },
                                onPlayPause = watch::playPause,
                                onClose = {
                                    // Stop first, then let go. The disposal that
                                    // follows only releases the connection.
                                    watch.stop()
                                    watching = null
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    // It sits *on* the tab bar, not over it. The
                                    // bar is how somebody leaves for another tab
                                    // while this keeps playing, which is the
                                    // whole point of a miniplayer.
                                    .padding(
                                        bottom = WindowInsets.navigationBars
                                            .asPaddingValues()
                                            .calculateBottomPadding() + Size.topBar,
                                    ),
                            )
                        } else {
                            WatchLayer(
                                onMinimise = { watching = session.copy(minimised = true) },
                            ) {
                                WatchScreen(
                                    viewModel = watch,
                                    mediaBaseUrl = baseUrl,
                                    onBack = { watching = session.copy(minimised = true) },
                                    onOpenVideo = { watching = WatchSession(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
