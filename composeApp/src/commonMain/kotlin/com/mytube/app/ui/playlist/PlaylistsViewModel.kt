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

    /**
     * Ask again, keeping what is drawn.
     *
     * Called on every arrival, because this ViewModel outlives the route and a
     * playlist made from the sheet on Home is otherwise missing here until the
     * app is restarted. Quiet, because blanking a list that is already correct
     * to redraw the same rows reads as a page that failed and recovered.
     */
    fun refresh() = load(showLoading = _state.value !is PlaylistsState.Ready)

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

    /**
     * Drop a playlist that has just been deleted from inside it.
     *
     * Told rather than refetched: this ViewModel lives in the activity's store
     * and outlives the route, so without being told the deleted row stayed on
     * the page and opened a collection the server no longer had. Refetching
     * would work too and would draw the stale list for the length of a round
     * trip — and this side already knows exactly which row went.
     */
    fun forget(playlistId: String) = update { ready ->
        ready.copy(playlists = ready.playlists.filterNot { it.id == playlistId })
    }

    private fun load(showLoading: Boolean = true) {
        if (showLoading) _state.value = PlaylistsState.Loading
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
