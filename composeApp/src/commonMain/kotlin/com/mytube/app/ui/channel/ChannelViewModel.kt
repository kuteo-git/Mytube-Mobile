package com.mytube.app.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Channel
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.SortOption
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.appendNew
import com.mytube.app.ui.watch.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChannelState {
    data object Loading : ChannelState
    data object NeedsServer : ChannelState
    data class Failed(val message: String) : ChannelState

    data class Ready(
        val channel: Channel,
        val videoCount: Int,
        val videos: List<Video>,
        val sortOptions: List<SortOption>,
        val sortToken: String,
        /**
         * Which option is lit, by **position** rather than by token.
         *
         * The tokens are not stable: every answer carries a fresh set, and the
         * one that was sent matches none of them. Lighting by token therefore
         * left the row with nothing lit the moment a sort landed — measured on
         * the simulator, and the reason pressing Popular read as doing nothing
         * at all. The order is the server's and it does not change within a
         * channel, so the position is the thing that survives the round trip.
         */
        val sortIndex: Int = 0,
        val nextPageToken: String,
        val loadingMore: Boolean = false,
        /**
         * A different order is on its way.
         *
         * Its own flag rather than dropping back to [Loading], because that is
         * what pressing Popular used to do: the header, the sort row and every
         * card vanished behind a full-page skeleton, so the control that had
         * just been pressed was no longer on screen — and when the page came
         * back it read as having reloaded rather than reordered. The list stays
         * up, dimmed, with the pressed segment lit.
         */
        val sorting: Boolean = false,
    ) : ChannelState
}

/**
 * One channel's page.
 *
 * The uploads come from **YouTube**, not the catalogue — the gateway asks
 * upstream because a scan only brings in the newest few dozen, so a page served
 * from the catalogue would cap a channel at that number with nothing to say why.
 * That is also why the sort options are the server's tokens rather than
 * something this app computes: they are InnerTube's own, and it cannot reorder
 * a list it does not hold.
 */
class ChannelViewModel(
    private val channelId: String,
    private val videos: VideoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ChannelState>(ChannelState.Loading)
    val state: StateFlow<ChannelState> = _state.asStateFlow()

    /** The id of the row being written, or empty. */
    private val _opening = MutableStateFlow("")
    val opening: StateFlow<String> = _opening.asStateFlow()

    /** The last attempt to open something upstream came back with nothing. */
    private val _openFailed = MutableStateFlow(false)
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    init {
        load(sortToken = "")
    }

    fun retry() {
        val current = _state.value as? ChannelState.Ready
        load(current?.sortToken.orEmpty(), index = current?.sortIndex ?: 0)
    }

    fun selectSort(option: SortOption) {
        val current = _state.value as? ChannelState.Ready ?: return
        val index = current.sortOptions.indexOf(option)
        if (index < 0 || index == current.sortIndex) return
        // Lit immediately, and the token recorded now rather than when the
        // answer lands. Without this the row stayed on the old segment for the
        // length of a round trip to YouTube, so a second press on the same
        // option started a second request — which is most of why the page
        // appeared to keep coming back sorted by Latest.
        _state.value = current.copy(
            sortToken = option.token,
            sortIndex = index,
            sorting = true,
        )
        load(option.token, keep = current, index = index)
    }

    /**
     * Opens a row, writing it into the catalogue first when it is not there yet.
     *
     * **This page's rows come from YouTube**, so most of them have no catalogue
     * entry — and navigating straight to one produced exactly what was reported:
     * *"gateway answered 404 for /api/videos/bnNMULP-Ftc"*, on a channel the
     * household had never imported. The video was fine; there was simply no row
     * to ask about.
     *
     * `SearchViewModel.openExternal` is the same three steps for the same
     * reason, and the rules it records hold here:
     *
     * - **The row is written first and the screen navigates second.** Going the
     *   other way puts the watch screen's "YouTube will not hand this video
     *   over" in front of a video that is on its way.
     * - **An empty id back is a refusal wearing a success's clothes.** The
     *   gateway answers 200 with `{"videoId":""}` when it could not resolve the
     *   address, and opening the watch screen on an empty id opens it on nothing.
     * - **`inLibrary` skips the round trip** for a row that already has one.
     *
     * The rest of the page is **not** translated here — that would be forty
     * writes for videos nobody has pressed. It travels as [QueueItem], carrying
     * the address and the flag, and `WatchViewModel` takes these same three
     * steps for each row as it is reached. It used to travel as bare ids with a
     * comment here claiming they took this path anyway; they did not, and
     * pressing next answered *"gateway answered 404"* for the second video of
     * every channel.
     *
     * The row just written is marked as being in the library on its way out, so
     * the screen this opens does not ask the gateway to write it a second time
     * — one call is one full metadata fetch upstream.
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

    /**
     * This row, now in the catalogue under [id].
     *
     * The address is carried rather than dropped: it is the only thing a row
     * can be re-resolved from, and nothing is gained by forgetting it.
     */
    private fun QueueItem.written(id: String) =
        copy(id = id, inLibrary = true)

    fun toggleSubscribed() {
        val current = _state.value as? ChannelState.Ready ?: return
        val next = !current.channel.subscribed
        // Drawn first, sent second, put back if refused — the same rule the
        // watch screen's buttons follow, and for the same reason.
        _state.value = current.copy(channel = current.channel.copy(subscribed = next))
        viewModelScope.launch {
            runCatching { videos.setSubscribed(channelId, next) }.onFailure {
                val now = _state.value
                if (now is ChannelState.Ready) _state.value = now.copy(channel = current.channel)
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? ChannelState.Ready ?: return
        if (current.loadingMore || current.nextPageToken.isEmpty()) return

        _state.update { current.copy(loadingMore = true) }
        viewModelScope.launch {
            _state.value = runCatching {
                videos.channelPage(channelId, current.sortToken, current.nextPageToken)
            }.fold(
                onSuccess = {
                    current.copy(
                        videos = current.videos.appendNew(it.videos),
                        nextPageToken = it.nextPageToken,
                        loadingMore = false,
                    )
                },
                // A page that failed leaves what is on screen alone. Replacing a
                // working list with an error because the *next* page failed
                // takes away what the viewer had.
                onFailure = { current.copy(loadingMore = false) },
            )
        }
    }

    /**
     * Fetch a page in this order.
     *
     * `keep` is what to leave on screen while it runs. Null on the first load —
     * there is nothing to keep, and the skeleton is right there — and the
     * current page when re-sorting, which is what stops the whole screen
     * blanking to change the order of a list already on it.
     */
    private fun load(sortToken: String, keep: ChannelState.Ready? = null, index: Int = 0) {
        if (keep == null) _state.value = ChannelState.Loading
        viewModelScope.launch {
            _state.value = runCatching { videos.channelPage(channelId, sortToken) }.fold(
                onSuccess = {
                    ChannelState.Ready(
                        channel = it.channel,
                        videoCount = it.videoCount,
                        videos = it.videos,
                        sortOptions = it.sortOptions,
                        sortToken = sortToken,
                        sortIndex = index,
                        nextPageToken = it.nextPageToken,
                    )
                },
                // A re-sort that fails keeps the page it had, with the segment
                // put back. Replacing a working list with an error message
                // because a *reordering* failed takes away what was being read.
                onFailure = { error ->
                    if (keep != null) keep.copy(sorting = false) else asState(error)
                },
            )
        }
    }

    private fun asState(error: Throwable): ChannelState = when (error) {
        is ServerNotConfigured -> ChannelState.NeedsServer
        else -> ChannelState.Failed(error.message ?: "could not reach the library")
    }
}
