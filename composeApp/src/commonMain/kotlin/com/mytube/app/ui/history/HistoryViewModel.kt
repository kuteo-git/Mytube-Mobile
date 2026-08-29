package com.mytube.app.ui.history

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
 * How much history one screen asks for.
 *
 * The endpoint pages and this does not, which is a deliberate difference from
 * the feed: history is something people scan for a video they remember watching,
 * and past a certain depth they search instead. Sixty is roughly a fortnight in
 * this household and one round trip.
 */
private const val HISTORY_LIMIT = 60

sealed interface HistoryState {
    data object Loading : HistoryState
    data object NeedsServer : HistoryState
    data class Failed(val message: String) : HistoryState
    data class Ready(
        val videos: List<Video>,
        val refreshing: Boolean = false,
    ) : HistoryState
}

class HistoryViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _state = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        load(showLoading = true)
    }

    fun refresh() {
        // As on Home: a pull keeps what is on screen and marks itself busy. Only
        // a first load or a retry after failure blanks the page.
        val keeping = _state.value is HistoryState.Ready
        load(showLoading = !keeping, refreshing = keeping)
    }

    private fun load(showLoading: Boolean, refreshing: Boolean = false) {
        val previous = _state.value as? HistoryState.Ready
        if (showLoading) _state.value = HistoryState.Loading
        else if (refreshing && previous != null) _state.update { previous.copy(refreshing = true) }

        viewModelScope.launch {
            _state.value = runCatching { videos.history(HISTORY_LIMIT) }.fold(
                onSuccess = { HistoryState.Ready(it) },
                onFailure = ::asState,
            )
        }
    }

    private fun asState(error: Throwable): HistoryState = when (error) {
        is ServerNotConfigured -> HistoryState.NeedsServer
        else -> HistoryState.Failed(error.message ?: "could not reach the library")
    }
}
