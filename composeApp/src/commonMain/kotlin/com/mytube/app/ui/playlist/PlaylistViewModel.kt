package com.mytube.app.ui.playlist

import com.mytube.app.ui.appendNew
import com.mytube.app.ui.toScreenState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
        if (ready.videos.none { it.id == video.id }) return
        // The header's count moves with the row. It is read from the first
        // answer, so taking only the row away left "1 video" over an empty page.
        _state.value = ready.copy(
            playlist = ready.playlist.copy(itemCount = (ready.playlist.itemCount - 1).coerceAtLeast(0)),
            videos = ready.videos.filterNot { it.id == video.id },
        )
        viewModelScope.launch {
            runCatching { videos.removeFromPlaylist(playlistId, video.id) }.onFailure {
                update { current ->
                    if (current.videos.any { it.id == video.id }) current
                    else current.copy(
                        playlist = current.playlist.copy(itemCount = current.playlist.itemCount + 1),
                        videos = ready.videos,
                    )
                }
            }
        }
    }

    private var loading: Job? = null

    /**
     * Every page, not the first.
     *
     * The list is the playlist, and it is also the queue Play all hands the
     * player — so a playlist of 139 that stopped at the gateway's first 24 was
     * a page missing most of its rows *and* an autoplay that ended at 24.
     * Reported from the phone. Followed here rather than on scroll for that
     * second reason: a queue cannot wait for somebody to scroll.
     *
     * On the first load each page is drawn as it lands. On a refresh the rows
     * already drawn stay until the whole answer is in, so a list that was 139
     * does not drop to 24 under a finger and grow back.
     */
    private fun load(showLoading: Boolean = true) {
        if (showLoading) _state.value = PlaylistState.Loading
        loading?.cancel()
        loading = viewModelScope.launch {
            val drawn = _state.value is PlaylistState.Ready
            val first = runCatching { videos.playlist(playlistId) }.getOrElse {
                if (it is CancellationException) throw it
                _state.value = it.toScreenState(PlaylistState.NeedsServer, PlaylistState::Failed)
                return@launch
            }
            if (!drawn) _state.value = PlaylistState.Ready(first.playlist, first.videos)
            var rows = first.videos
            var token = first.nextPageToken
            while (token.isNotEmpty()) {
                // A later page that fails leaves what is on screen alone —
                // on a refresh, the whole list drawn before it; pull to
                // refresh asks again.
                val page = runCatching { videos.playlist(playlistId, token) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull() ?: return@launch
                rows = rows.appendNew(page.videos)
                // Into the state as it is now: a row removed while the rest
                // was arriving must not come back with the next page.
                if (!drawn) update { it.copy(videos = it.videos.appendNew(page.videos)) }
                token = page.nextPageToken
            }
            if (drawn) _state.value = PlaylistState.Ready(first.playlist, rows)
        }
    }

    private inline fun update(block: (PlaylistState.Ready) -> PlaylistState.Ready) {
        val ready = _state.value as? PlaylistState.Ready ?: return
        _state.value = block(ready)
    }
}
