package com.mytube.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the home screen can be.
 *
 * A sealed type rather than a bag of nullable fields and booleans, because the
 * states are genuinely exclusive and the compiler can say so. `isLoading` beside
 * `error` beside `videos` allows sixteen combinations, of which four make sense,
 * and the other twelve are drawn as something nobody designed.
 *
 * `NeedsServer` is its own case rather than an error message. Nothing has gone
 * wrong when an app has not been set up yet, and the screen's answer is a button
 * to the settings rather than a retry that will fail identically.
 */
sealed interface HomeState {
    data object Loading : HomeState
    data object NeedsServer : HomeState
    data class Failed(val message: String) : HomeState
    data class Ready(
        val videos: List<Video>,
        /** Empty when there is no page after this one — nothing here is nullable. */
        val nextPageToken: String,
        /** A further page is on its way; the list below stays on screen. */
        val loadingMore: Boolean = false,
    ) : HomeState
}

/**
 * The home feed's state holder.
 *
 * ## Why a ViewModel and not remembered state in the composable
 *
 * The feed survives a rotation, a process moving to the background and coming
 * back, and — on the phone layouts this app is for — the watch screen opening
 * over the top of it. State held in a composable is thrown away by all three,
 * and the cost is a request to the household's own server plus a scroll position
 * nobody asked to lose.
 *
 * ## Why it takes a repository interface
 *
 * It is constructed at the composition root, so this class never learns whether
 * the answer came from the gateway or from a fake. That is what makes it
 * testable without a server, and the architecture guard is what keeps it true.
 */
class HomeViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = HomeState.Loading
        viewModelScope.launch {
            _state.value = runCatching { videos.feed() }.fold(
                onSuccess = { HomeState.Ready(it.videos, it.nextPageToken) },
                onFailure = ::asState,
            )
        }
    }

    /**
     * The next page, appended.
     *
     * Guarded against being asked twice: a list at the bottom of its scroll fires
     * this on every frame it stays there, and without the guard a slow answer
     * becomes a dozen identical requests and a dozen copies of the same page.
     */
    fun loadMore() {
        val current = _state.value
        if (current !is HomeState.Ready || current.loadingMore) return
        if (current.nextPageToken.isEmpty()) return

        _state.update { current.copy(loadingMore = true) }
        viewModelScope.launch {
            _state.value = runCatching { videos.feed(pageToken = current.nextPageToken) }.fold(
                onSuccess = {
                    HomeState.Ready(current.videos + it.videos, it.nextPageToken)
                },
                // A page that failed to arrive leaves what is already on screen
                // alone. Replacing a working list with an error because the
                // *next* page failed is losing something the viewer had.
                onFailure = { current.copy(loadingMore = false) },
            )
        }
    }

    private fun asState(error: Throwable): HomeState = when (error) {
        is ServerNotConfigured -> HomeState.NeedsServer
        else -> HomeState.Failed(error.message ?: "could not reach the library")
    }
}
