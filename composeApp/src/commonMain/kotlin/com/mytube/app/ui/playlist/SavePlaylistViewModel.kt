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

    /** The catalogue id an upstream result was written as, once it has been. */
    private var ensured: String? = null

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
     * Make the playlist, put this video in it, and leave the row ticked.
     *
     * **Both acts, on one press.** Somebody who names a list for this video has
     * said where the video goes; leaving the add to a second press of Save would
     * make a named-but-empty playlist the outcome of cancelling — and the sheet
     * is closed over the alert, so that second press is not even on screen.
     *
     * The row is then ticked *and* part of [original], so pressing Save
     * afterwards sends nothing about it rather than adding it twice.
     */
    fun create() {
        val ready = _state.value as? SaveSheetState.Ready ?: return
        val name = ready.newName.trim()
        if (name.isEmpty()) return
        _state.value = ready.copy(creating = false, newName = "", saving = true)
        viewModelScope.launch {
            val made = runCatching { videos.createPlaylist(name) }.getOrElse {
                // Back to the alert with what was typed still in it: a name
                // retyped from memory is the worst thing to ask of somebody
                // whose request has just failed.
                update { it.copy(saving = false, creating = true, newName = name) }
                return@launch
            }
            val videoId = resolveVideoId()
            if (videoId.isNotEmpty()) {
                runCatching { videos.addToPlaylist(made.id, videoId) }
                    .onSuccess { original = original + made.id }
            }
            update { current ->
                current.copy(
                    // First, not last. The sheet's list is capped and scrolls,
                    // so appending put a playlist somebody had just named below
                    // the fold — measured, and the whole point of showing the
                    // sheet again is that they can see it happened. It is also
                    // the server's own order: it lists by `updated_at`, and a
                    // playlist just made is the most recently touched.
                    playlists = listOf(made.copy(itemCount = 1)) + current.playlists,
                    ticked = current.ticked + made.id,
                    saving = false,
                )
            }
        }
    }

    /**
     * The catalogue id to write playlist rows against.
     *
     * For an upstream result there is none until one is made — the row is
     * written first and its answer names the video, which is
     * `SearchViewModel.openExternal`'s pattern. Empty means the gateway could
     * not resolve the address: a refusal wearing a success's clothes, and adding
     * with it would write rows naming no video.
     *
     * Remembered, so creating a playlist and then pressing Save does not write
     * the same catalogue row twice.
     */
    private suspend fun resolveVideoId(): String {
        if (!target.isExternal) return target.videoId
        ensured?.let { return it }
        val id = runCatching { videos.ensureExternal(target.sourceUrl) }.getOrElse { "" }
        ensured = id
        return id
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
            val videoId = resolveVideoId()
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
