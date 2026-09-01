package com.mytube.app.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistsState {
    data object Loading : PlaylistsState
    data object NeedsServer : PlaylistsState
    data class Failed(val message: String) : PlaylistsState
    data class Ready(
        val playlists: List<Playlist>,
        val creating: Boolean = false,
        val newName: String = "",
    ) : PlaylistsState
}

/**
 * The member's collections, reached from Settings.
 *
 * The saved shelf is **not** in this list and is drawn by the screen as a fixed
 * first row, for the reason `Playlist` states: it is the pinned set rather than
 * a row the server could delete.
 */
class PlaylistsViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistsState>(PlaylistsState.Loading)
    val state: StateFlow<PlaylistsState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    fun startCreating() = update { it.copy(creating = true) }

    fun cancelCreating() = update { it.copy(creating = false, newName = "") }

    fun nameChanged(name: String) = update { it.copy(newName = name) }

    fun create() {
        val ready = _state.value as? PlaylistsState.Ready ?: return
        val name = ready.newName.trim()
        if (name.isEmpty()) return
        _state.value = ready.copy(creating = false, newName = "")
        viewModelScope.launch {
            runCatching { videos.createPlaylist(name) }.onSuccess { made ->
                // Newest first, which is the order the server lists them in:
                // it sorts by `updated_at`, and a playlist just made is the one
                // most recently touched.
                update { it.copy(playlists = listOf(made) + it.playlists) }
            }
        }
    }

    private fun load() {
        _state.value = PlaylistsState.Loading
        viewModelScope.launch {
            _state.value = runCatching { videos.playlists() }.fold(
                onSuccess = { PlaylistsState.Ready(it) },
                onFailure = {
                    when (it) {
                        is ServerNotConfigured -> PlaylistsState.NeedsServer
                        else -> PlaylistsState.Failed(
                            it.message ?: "could not reach the library",
                        )
                    }
                },
            )
        }
    }

    private inline fun update(block: (PlaylistsState.Ready) -> PlaylistsState.Ready) {
        val ready = _state.value as? PlaylistsState.Ready ?: return
        _state.value = block(ready)
    }
}
