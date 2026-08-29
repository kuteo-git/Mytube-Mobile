package com.mytube.app.ui

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
import com.mytube.app.ui.settings.ServerSetupScreen
import com.mytube.app.ui.settings.ServerSetupViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.mytube.app.ui.i18n.LocalStrings
import com.mytube.app.ui.i18n.deviceLanguage
import com.mytube.app.ui.theme.MytubeTheme

/**
 * Which screen is showing.
 *
 * Two of them so far, and no navigation library. Compose Navigation earns its
 * keep with a back stack, deep links and state saved across several levels; none
 * of that exists yet, and choosing it now would be picking a dependency before
 * the shape it serves is known. The decision gets made properly when the watch
 * screen arrives — which per the server charter is *a layer over the tab
 * underneath*, dismissed by dragging down, and that requirement should be in
 * view when a library is chosen rather than discovered afterwards.
 */
private sealed interface Route {
    data object Deciding : Route
    data object Setup : Route
    data object Home : Route
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

            when (route) {
                is Route.Deciding -> Unit

                is Route.Setup -> ServerSetupScreen(
                    viewModel = viewModel { ServerSetupViewModel(container.serverRepository) },
                    onDone = { route = Route.Home },
                )

                is Route.Home -> HomeScreen(
                    // Keyed on the address: changing it has to build a new
                    // HomeViewModel, because the old one is holding a feed fetched
                    // from somewhere else.
                    viewModel = viewModel(key = "home-$baseUrl") {
                        HomeViewModel(container.videoRepository)
                    },
                    mediaBaseUrl = baseUrl,
                    onOpenSettings = { route = Route.Setup },
                )
            }
        }
    }
}
