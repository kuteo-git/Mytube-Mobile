package com.mytube.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How long to wait after the last keystroke before asking.
 *
 * A search is a full-text query plus a recorded behaviour signal — the gateway
 * records one per request — so typing "nothing phone" without this is thirteen
 * queries and thirteen signals for one intent, and recsys would be learning
 * every prefix of what somebody typed.
 */
private const val DEBOUNCE_MILLIS = 300L

sealed interface SearchState {
    /** Nothing typed yet. Not an empty result: nobody has asked anything. */
    data object Idle : SearchState
    data object Searching : SearchState
    data object NeedsServer : SearchState
    data class Failed(val message: String) : SearchState
    data class Ready(val query: String, val videos: List<Video>) : SearchState
}

class SearchViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var pending: Job? = null

    fun type(text: String) {
        _query.value = text
        // Cancelled on every keystroke, so only the last one survives the wait.
        // Without this the debounce delays each request without reducing how
        // many are eventually made.
        pending?.cancel()

        if (text.isBlank()) {
            _state.value = SearchState.Idle
            return
        }

        pending = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            // Only blank the screen on the *first* search. Replacing results
            // with a spinner on every keystroke makes a list that is mostly
            // still correct flicker away while somebody refines a word.
            if (_state.value !is SearchState.Ready) _state.value = SearchState.Searching
            _state.value = runCatching { videos.search(text) }.fold(
                onSuccess = { SearchState.Ready(text, it) },
                onFailure = ::asState,
            )
        }
    }

    fun clear() {
        pending?.cancel()
        _query.value = ""
        _state.value = SearchState.Idle
    }

    fun retry() {
        val text = _query.value
        if (text.isNotBlank()) type(text)
    }

    private fun asState(error: Throwable): SearchState = when (error) {
        is ServerNotConfigured -> SearchState.NeedsServer
        else -> SearchState.Failed(error.message ?: "could not reach the library")
    }
}
