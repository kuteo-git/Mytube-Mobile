package com.mytube.app.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistState {
    data object Loading : PlaylistState
    data object NeedsServer : PlaylistState
    data class Failed(val message: String) : PlaylistState
    data class Ready(
        val playlist: Playlist,
        val videos: List<Video>,
        val renaming: Boolean = false,
        val newName: String = "",
        val confirmingDelete: Boolean = false,
    ) : PlaylistState

    /**
     * The playlist was deleted from this page; the caller leaves it.
     *
     * Carries the id, so the page behind can drop the row rather than refetch
     * the list it is about to draw.
     */
    data class Deleted(val playlistId: String) : PlaylistState
}

/** One collection, and what is in it. */
class PlaylistViewModel(
    private val playlistId: String,
    private val videos: VideoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistState>(PlaylistState.Loading)
    val state: StateFlow<PlaylistState> = _state.asStateFlow()

    init {
        load()
    }

    /** Ask again, keeping what is drawn. See `PlaylistsViewModel.refresh`. */
    fun refresh() = load(showLoading = _state.value !is PlaylistState.Ready)

    fun startRenaming() = update { it.copy(renaming = true, newName = it.playlist.title) }

    fun cancelRenaming() = update { it.copy(renaming = false, newName = "") }

    fun nameChanged(name: String) = update { it.copy(newName = name) }

    fun rename() {
        val ready = _state.value as? PlaylistState.Ready ?: return
        val name = ready.newName.trim()
        if (name.isEmpty()) return
        // Drawn first and sent second, as every other opinion in this app is —
        // and put back if the server refuses, which is the other half of that
        // rule and the half people forget.
        _state.value = ready.copy(
            playlist = ready.playlist.copy(title = name),
            renaming = false,
            newName = "",
        )
        viewModelScope.launch {
            runCatching { videos.updatePlaylist(playlistId, name) }.onFailure {
                update { it.copy(playlist = it.playlist.copy(title = ready.playlist.title)) }
            }
        }
    }

    fun askDelete() = update { it.copy(confirmingDelete = true) }

    fun cancelDelete() = update { it.copy(confirmingDelete = false) }

    fun delete() {
        val ready = _state.value as? PlaylistState.Ready ?: return
        viewModelScope.launch {
            runCatching { videos.deletePlaylist(ready.playlist.id) }.fold(
                onSuccess = { _state.value = PlaylistState.Deleted(ready.playlist.id) },
                onFailure = { update { it.copy(confirmingDelete = false) } },
            )
        }
    }

    /**
     * Take one video off this page.
     *
     * **Put back when the request fails**, unlike the feed's "not interested".
     * That list is a ranking and a row that failed to leave is gone from the
     * page either way; this page *is* the playlist, so a row still in it that is
     * not drawn is the screen lying about its own contents.
     */
    fun remove(video: Video) {
        val ready = _state.value as? PlaylistState.Ready ?: return
        _state.value = ready.copy(videos = ready.videos.filterNot { it.id == video.id })
        viewModelScope.launch {
            runCatching { videos.removeFromPlaylist(playlistId, video.id) }.onFailure {
                update { current ->
                    if (current.videos.any { it.id == video.id }) current
                    else current.copy(videos = ready.videos)
                }
            }
        }
    }

    private fun load(showLoading: Boolean = true) {
        if (showLoading) _state.value = PlaylistState.Loading
        viewModelScope.launch {
            _state.value = runCatching { videos.playlist(playlistId) }.fold(
                onSuccess = { PlaylistState.Ready(it.playlist, it.videos) },
                onFailure = {
                    when (it) {
                        is ServerNotConfigured -> PlaylistState.NeedsServer
                        else -> PlaylistState.Failed(
                            it.message ?: "could not reach the library",
                        )
                    }
                },
            )
        }
    }

    private inline fun update(block: (PlaylistState.Ready) -> PlaylistState.Ready) {
        val ready = _state.value as? PlaylistState.Ready ?: return
        _state.value = block(ready)
    }
}
