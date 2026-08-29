package com.mytube.app.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SubscriptionsState {
    data object Loading : SubscriptionsState
    data object NeedsServer : SubscriptionsState
    data class Failed(val message: String) : SubscriptionsState
    data class Ready(
        val channels: List<Channel>,
        val refreshing: Boolean = false,
    ) : SubscriptionsState
}

/**
 * Who this member follows.
 *
 * Sorted by name here rather than asked for in that order. The server returns
 * them in whatever order the catalogue holds, and a list somebody scans for a
 * particular channel wants one predictable order — while sorting on the server
 * would be a query change affecting every other reader of that endpoint.
 */
class SubscriptionsViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _state = MutableStateFlow<SubscriptionsState>(SubscriptionsState.Loading)
    val state: StateFlow<SubscriptionsState> = _state.asStateFlow()

    init {
        load(showLoading = true)
    }

    fun refresh() {
        val keeping = _state.value is SubscriptionsState.Ready
        load(showLoading = !keeping, refreshing = keeping)
    }

    private fun load(showLoading: Boolean, refreshing: Boolean = false) {
        val previous = _state.value as? SubscriptionsState.Ready
        if (showLoading) _state.value = SubscriptionsState.Loading
        else if (refreshing && previous != null) _state.update { previous.copy(refreshing = true) }

        viewModelScope.launch {
            _state.value = runCatching { videos.subscriptions() }.fold(
                onSuccess = { channels ->
                    SubscriptionsState.Ready(channels.sortedBy { it.name.lowercase() })
                },
                onFailure = ::asState,
            )
        }
    }

    private fun asState(error: Throwable): SubscriptionsState = when (error) {
        is ServerNotConfigured -> SubscriptionsState.NeedsServer
        else -> SubscriptionsState.Failed(error.message ?: "could not reach the library")
    }
}
