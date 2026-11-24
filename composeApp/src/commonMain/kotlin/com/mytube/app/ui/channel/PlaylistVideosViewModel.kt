package com.mytube.app.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.appendNew
import com.mytube.app.ui.distinctById
import com.mytube.app.ui.toScreenState
import com.mytube.app.ui.watch.QueueItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One of a channel's playlists, opened from its Playlists tab.
 *
 * The videos come from **YouTube**, not the catalogue, the same as the channel
 * page's — and for the same reason the channel page writes a row before it
 * opens one: a playlist mixes channels and most of its videos have never been
 * near this disk. Opening a video takes [ChannelViewModel.openVideo]'s three
 * steps, so the two share nothing but agree on the rule.
 */
sealed interface PlaylistVideosState {
    data object Loading : PlaylistVideosState
    data object NeedsServer : PlaylistVideosState
    data class Failed(val message: String) : PlaylistVideosState

    data class Ready(
        /** The playlist's own title. Seeded from the card, confirmed by the server. */
        val title: String,
        val videos: List<Video>,
        val nextPageToken: String,
        val loadingMore: Boolean = false,
    ) : PlaylistVideosState {
        val hasMore: Boolean get() = nextPageToken.isNotEmpty()
    }
}

class PlaylistVideosViewModel(
    private val channelId: String,
    private val playlistId: String,
    /** The title known before the first page lands, so the header never flashes empty. */
    private val initialTitle: String,
    private val videos: VideoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistVideosState>(PlaylistVideosState.Loading)
    val state: StateFlow<PlaylistVideosState> = _state.asStateFlow()

    /** The id of the row being written, or empty. */
    private val _opening = MutableStateFlow("")
    val opening: StateFlow<String> = _opening.asStateFlow()

    /** The last attempt to open something upstream came back with nothing. */
    private val _openFailed = MutableStateFlow(false)
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = PlaylistVideosState.Loading
        viewModelScope.launch {
            _state.value = runCatching { videos.playlistVideos(channelId, playlistId) }.fold(
                onSuccess = { page ->
                    PlaylistVideosState.Ready(
                        // The server's title wins once it lands; the card's is only
                        // there to fill the header until it does.
                        title = page.title.ifEmpty { initialTitle },
                        videos = page.videos.distinctById(),
                        nextPageToken = page.nextPageToken,
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    error.toScreenState(PlaylistVideosState.NeedsServer, PlaylistVideosState::Failed)
                },
            )
        }
    }

    fun loadMore() {
        val current = _state.value as? PlaylistVideosState.Ready ?: return
        if (current.loadingMore || current.nextPageToken.isEmpty()) return

        _state.update { current.copy(loadingMore = true) }
        viewModelScope.launch {
            val page = runCatching {
                videos.playlistVideos(channelId, playlistId, current.nextPageToken)
            }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            _state.update { now ->
                if (now !is PlaylistVideosState.Ready) return@update now
                // A next page that failed leaves what is on screen alone.
                if (page == null) now.copy(loadingMore = false)
                else now.copy(
                    videos = now.videos.appendNew(page.videos),
                    nextPageToken = page.nextPageToken,
                    loadingMore = false,
                )
            }
        }
    }

    /**
     * Opens a row, writing it into the catalogue first when it is not there yet.
     *
     * The same three steps as [ChannelViewModel.openVideo], and the same reasons:
     * a playlist's rows come from YouTube, an empty id back is a refusal, and
     * `inLibrary` skips the round trip. The rest of the list travels as
     * [QueueItem]s so next stays inside this playlist in this order.
     */
    fun openVideo(
        video: Video,
        queue: List<QueueItem>,
        onOpened: (String, List<QueueItem>) -> Unit,
    ) {
        if (_opening.value.isNotEmpty()) return
        _openFailed.value = false

        if (video.inLibrary || video.sourceUrl.isEmpty()) {
            onOpened(video.id, queue)
            return
        }

        _opening.value = video.id
        viewModelScope.launch {
            runCatching { videos.ensureExternal(video.sourceUrl) }
                .onSuccess { id ->
                    if (id.isNotEmpty()) {
                        onOpened(id, queue.map { if (it.id == video.id) it.written(id) else it })
                    } else {
                        _openFailed.value = true
                    }
                }
                .onFailure { _openFailed.value = true }
            _opening.value = ""
        }
    }
}
