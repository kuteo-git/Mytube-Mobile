package com.mytube.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SavedState {
    data object Loading : SavedState
    data object NeedsServer : SavedState
    data class Failed(val message: String) : SavedState
    data class Ready(val videos: List<Video>) : SavedState
}

/**
 * The shelf: videos this member has kept.
 *
 * There was a Save action in every menu and nowhere to see the result, which is
 * the same defect as a dead button one step removed — the press did something
 * real and nothing in the app could show it.
 */
class SavedViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _state = MutableStateFlow<SavedState>(SavedState.Loading)
    val state: StateFlow<SavedState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    /**
     * Take one off the shelf, from the shelf itself.
     *
     * Removed from the list rather than redrawn unsaved: this screen *is* the
     * saved videos, so one that is no longer saved does not belong on it. Not
     * put back if the request fails — the next load is where the truth shows,
     * and a row reappearing a second later is the more confusing outcome.
     */
    fun unsave(video: Video) {
        val current = _state.value as? SavedState.Ready ?: return
        _state.value = SavedState.Ready(current.videos.filterNot { it.id == video.id })
        viewModelScope.launch { runCatching { videos.setSaved(video.id, false) } }
    }

    private fun load() {
        _state.value = SavedState.Loading
        viewModelScope.launch {
            _state.value = runCatching { videos.saved() }.fold(
                onSuccess = { SavedState.Ready(it) },
                onFailure = ::asState,
            )
        }
    }

    private fun asState(error: Throwable): SavedState = when (error) {
        is ServerNotConfigured -> SavedState.NeedsServer
        else -> SavedState.Failed(error.message ?: "could not reach the library")
    }
}
