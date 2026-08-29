package com.mytube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
import com.mytube.app.ui.i18n.deviceLanguage
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
    data class Watch(val videoId: String) : Route
}

/**
 * The root.
 *
 * Takes the container rather than reaching for a global: every dependency any
 * screen has arrives through this one argument, which is what makes the graph
 * something a reader can follow and a test can replace.
 */
@Composable
fun App(container: AppContainer) {
    // Read once per process. There is no language switch yet, so nothing can
    // change it while the app runs; when that screen arrives this becomes state
    // and the provider below is already in the right place.
    val strings = remember { deviceLanguage().strings }

    CompositionLocalProvider(LocalStrings provides strings) {
        MytubeTheme {
            var route: Route by remember { mutableStateOf(Route.Deciding) }
            var baseUrl by remember { mutableStateOf("") }

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

                // Home and Watch are one branch, not two. The watch screen is
                // a layer *over* the tab, so the tab has to be composed
                // underneath it — otherwise dragging down reveals an empty
                // background and the feed snaps in at the end.
                is Route.Home, is Route.Watch -> Box(Modifier.fillMaxSize()) {
                    AppShell(
                        current = Tab.Home,
                        // The other three tabs are screens that do not exist
                        // yet. They are drawn because the bar is the app's shape
                        // and a bar with one item is not it — and pressing them
                        // does nothing rather than pretending, which is the
                        // honest state until the screens arrive.
                        onSelect = { if (it == Tab.Settings) route = Route.Setup },
                    ) {
                        HomeScreen(
                            // Keyed on the address: changing it builds a new
                            // HomeViewModel, because the old one holds a feed
                            // fetched from somewhere else.
                            viewModel = viewModel(key = "home-$baseUrl") {
                                HomeViewModel(container.videoRepository)
                            },
                            mediaBaseUrl = baseUrl,
                            onOpenSettings = { route = Route.Setup },
                            onOpenVideo = { route = Route.Watch(it) },
                        )
                    }

                    if (current is Route.Watch) {
                        WatchLayer(onDismiss = { route = Route.Home }) {
                            WatchScreen(
                                // Keyed on the video: opening another one builds
                                // a new ViewModel, because the old one owns a
                                // player pointed at the previous stream — and
                                // releases it when it is cleared.
                                viewModel = viewModel(key = "watch-${current.videoId}") {
                                    WatchViewModel(
                                        videoId = current.videoId,
                                        mediaBaseUrl = baseUrl,
                                        videos = container.videoRepository,
                                        streams = container.streamRepository,
                                        playerFactory = container.playerFactory,
                                    )
                                },
                                mediaBaseUrl = baseUrl,
                                onBack = { route = Route.Home },
                            )
                        }
                    }
                }
            }
        }
    }
}
