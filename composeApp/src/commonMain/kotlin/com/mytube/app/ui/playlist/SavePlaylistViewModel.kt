package com.mytube.app.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.domain.model.Playlist
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the sheet was opened for.
 *
 * A library video carries its id; an upstream result carries only an address,
 * and there is no catalogue row to add until somebody asks for one. Both are one
 * type because the sheet draws the same rows either way — the difference is a
 * single round trip on Save, not a second screen.
 */
data class SaveTarget(
    val videoId: String,
    /** Set for an upstream result, empty for anything already in the library. */
    val sourceUrl: String = "",
    /** Whether the pinned bit is already on, which the card that opened this knows. */
    val saved: Boolean = false,
) {
    val isExternal: Boolean get() = videoId.isEmpty() && sourceUrl.isNotEmpty()
}

sealed interface SaveSheetState {
    data object Loading : SaveSheetState
    data class Failed(val message: String) : SaveSheetState
    data class Ready(
        /** The default playlist — the saved shelf — which is not a [Playlist]. */
        val savedTicked: Boolean,
        val playlists: List<Playlist>,
        /** The ids ticked right now, which starts as what the server answered. */
        val ticked: Set<String>,
        /** Whether the inline name field is open. */
        val creating: Boolean,
        val newName: String,
        val saving: Boolean,
    ) : SaveSheetState
}

/**
 * The sheet behind the bookmark.
 *
 * ## Save applies the difference, one request per change
 *
 * Not one call carrying the final state. A tap is usually one or two changes,
 * and an endpoint that takes the whole state is an endpoint that empties a
 * playlist the day a client is wrong about what was in it. So the diff is
 * `ticked - original` to add and `original - ticked` to remove, plus the pinned
 * bit when it moved — and unticking really removes, because a tick that does not
 * is a control that lies.
 *
 * ## The pinned state is passed in, not fetched
 *
 * The card that opened the sheet already knows it (`Video.saved`). A second
 * request for a fact in hand is a slower sheet for nothing.
 */
class SavePlaylistViewModel(
    private val target: SaveTarget,
    private val videos: VideoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SaveSheetState>(SaveSheetState.Loading)
    val state: StateFlow<SaveSheetState> = _state.asStateFlow()

    /** What the server said when the sheet opened, which the diff is against. */
    private var original: Set<String> = emptySet()
    private var originallySaved = target.saved

    init {
        load()
    }

    fun retry() = load()

    fun toggleSaved() = update { it.copy(savedTicked = !it.savedTicked) }

    fun toggle(playlistId: String) = update { ready ->
        ready.copy(
            ticked = if (playlistId in ready.ticked) {
                ready.ticked - playlistId
            } else {
                ready.ticked + playlistId
            },
        )
    }

    fun startCreating() = update { it.copy(creating = true) }

    fun cancelCreating() = update { it.copy(creating = false, newName = "") }

    fun nameChanged(name: String) = update { it.copy(newName = name) }

    /**
     * Make the playlist, and leave it ticked.
     *
     * Somebody who has just named a list for this video means to put the video
     * in it; making them tick the row they invented would be asking the same
     * question twice.
     */
    fun create() {
        val ready = _state.value as? SaveSheetState.Ready ?: return
        val name = ready.newName.trim()
        if (name.isEmpty()) return
        _state.value = ready.copy(creating = false, newName = "", saving = true)
        viewModelScope.launch {
            runCatching { videos.createPlaylist(name) }.fold(
                onSuccess = { made ->
                    update { current ->
                        current.copy(
                            playlists = current.playlists + made,
                            ticked = current.ticked + made.id,
                            saving = false,
                        )
                    }
                },
                onFailure = { update { it.copy(saving = false, creating = true, newName = name) } },
            )
        }
    }

    /**
     * Apply the difference, then tell the caller it is done.
     *
     * [onDone] carries the pinned bit so the card that opened the sheet can
     * redraw its own bookmark without asking the server what it already knows.
     */
    fun save(onDone: (saved: Boolean) -> Unit) {
        val ready = _state.value as? SaveSheetState.Ready ?: return
        if (!hasChanges(ready)) {
            onDone(ready.savedTicked)
            return
        }
        _state.value = ready.copy(saving = true)
        viewModelScope.launch {
            // An upstream result has no catalogue row yet, and every add below
            // needs its id. The pattern is `SearchViewModel.openExternal`'s: the
            // row is written first, and only its answer names the video.
            val videoId = if (target.isExternal) {
                runCatching { videos.ensureExternal(target.sourceUrl) }.getOrElse { "" }
            } else {
                target.videoId
            }
            // The gateway answers 200 with an empty id when it could not resolve
            // the address — a refusal wearing a success's clothes. Adding to
            // playlists with it would write rows naming no video.
            if (videoId.isEmpty()) {
                update { it.copy(saving = false) }
                return@launch
            }

            for (id in ready.ticked - original) {
                runCatching { videos.addToPlaylist(id, videoId) }
            }
            for (id in original - ready.ticked) {
                runCatching { videos.removeFromPlaylist(id, videoId) }
            }
            if (ready.savedTicked != originallySaved) {
                runCatching { videos.setSaved(videoId, ready.savedTicked) }
            }
            original = ready.ticked
            originallySaved = ready.savedTicked
            update { it.copy(saving = false) }
            onDone(ready.savedTicked)
        }
    }

    /** Nothing to apply means nothing to press — the button says so. */
    fun hasChanges(ready: SaveSheetState.Ready): Boolean =
        ready.ticked != original || ready.savedTicked != originallySaved

    private fun load() {
        _state.value = SaveSheetState.Loading
        viewModelScope.launch {
            // An upstream result is in no playlist yet, by definition — there is
            // no row for it to be in — so the lists are asked for without it.
            runCatching { videos.playlists(if (target.isExternal) "" else target.videoId) }.fold(
                onSuccess = { lists ->
                    original = lists.filter { it.containsVideo }.map { it.id }.toSet()
                    _state.value = SaveSheetState.Ready(
                        savedTicked = target.saved,
                        playlists = lists,
                        ticked = original,
                        creating = false,
                        newName = "",
                        saving = false,
                    )
                },
                onFailure = {
                    _state.value =
                        SaveSheetState.Failed(it.message ?: "could not reach the library")
                },
            )
        }
    }

    private inline fun update(block: (SaveSheetState.Ready) -> SaveSheetState.Ready) {
        val ready = _state.value as? SaveSheetState.Ready ?: return
        _state.value = block(ready)
    }
}
