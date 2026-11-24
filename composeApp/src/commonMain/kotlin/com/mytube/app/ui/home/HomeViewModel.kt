package com.mytube.app.ui.home

import com.mytube.app.ui.toScreenState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.appendNew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * One chip above the feed.
 *
 * The row is a fixed, curated set rather than the server's topic list (see
 * [HomeChips]). `Missed`, `All` and `Live` are not topics — `Missed` and `Live`
 * are answered from their own endpoints — and `Category` is one of the handful
 * of subjects promoted onto the row. `More` is not a feed at all: it is the
 * button to the More page, where Live and (later) Chart live.
 */
sealed interface Chip {
    data object Missed : Chip
    data object All : Chip
    data object Live : Chip
    data class Category(
        /** The topic the server filters the feed by. */
        val query: String,
        /** The short name on the chip, which need not equal [query]. */
        val label: String,
    ) : Chip
    data object More : Chip

    val key: String
        get() = when (this) {
            Missed -> "__missed"
            All -> "__all"
            Live -> "__live"
            is Category -> query
            More -> "__more"
        }
}

/**
 * The home chip row, fixed rather than built from the server's topics.
 *
 * [row] is both what the chip strip draws and the pager's pages, in order — so
 * [Chip.More] is a page you swipe to, like YouTube's own More tab, not a button
 * that leaves. Its page is a short menu (see `MoreContent`); the feed chips
 * before it are each a feed.
 */
object HomeChips {
    val row: List<Chip> = listOf(
        Chip.Missed,
        Chip.All,
        Chip.Category(query = "Gaming", label = "Gaming"),
        Chip.Category(query = "Music", label = "Music"),
        Chip.More,
    )

    /**
     * News sits inside the More page, not on the row, as its first entry. It is
     * YouTube's "News & Politics" category — the gateway's `feed` filters the
     * catalogue by it, so the page has the catalogue's news videos.
     */
    val news: Chip.Category = Chip.Category(query = "News & Politics", label = "News")
}

sealed interface HomeState {
    data object Loading : HomeState
    data object NeedsServer : HomeState
    data class Failed(val message: String) : HomeState

    data class Ready(
        val videos: List<Video>,
        val nextPageToken: String,
        val loadingMore: Boolean = false,
        /** A pull-to-refresh is in flight; the list below stays on screen. */
        val refreshing: Boolean = false,
    ) : HomeState
}

/**
 * One chip's feed.
 *
 * A single-chip holder, one per page of the home pager — the chip row is a
 * fixed set now, so nothing here decides which chips exist; each instance only
 * loads, pages and refreshes its own feed. Keyed on the server address and
 * profile by its caller, so pointing the app at another machine, or switching
 * profile, cannot show one feed under another's chip.
 */
class HomeViewModel(
    private val videos: VideoRepository,
    private val chip: Chip,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /**
     * The page being fetched, and the next page being fetched.
     *
     * Kept so a new load can cancel both. Without them the answer that arrived
     * last won, not the one asked for last.
     */
    private var loading: Job? = null
    private var loadingMore: Job? = null

    init {
        load(showLoading = true)
    }

    fun refresh() {
        // A pull-to-refresh keeps what is on screen and marks itself busy; only
        // a first load or a retry after failure blanks the page.
        val keepingContent = _state.value is HomeState.Ready
        load(showLoading = !keepingContent, refreshing = keepingContent)
    }

    /**
     * Take it off the page, and tell the ranker.
     *
     * Removed from the list rather than greyed out. It is not put back if the
     * request fails: the row is gone from the viewer's page either way, and the
     * next feed load is where the truth shows.
     */
    fun notInterested(video: Video) {
        val current = _state.value as? HomeState.Ready ?: return
        _state.value = current.copy(videos = current.videos.filterNot { it.id == video.id })
        viewModelScope.launch { runCatching { videos.setNotInterested(video.id) } }
    }

    fun loadMore() {
        val current = _state.value
        if (current !is HomeState.Ready || current.loadingMore || current.refreshing) return
        if (current.nextPageToken.isEmpty()) return
        // Live is a list, not a feed: the server sends everything on air in one
        // answer with no page token, so there is nothing to ask for.
        if (chip == Chip.Live) return

        _state.update { current.copy(loadingMore = true) }
        loadingMore = viewModelScope.launch {
            val page = runCatching { fetch(current.nextPageToken) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            // Merged into the state as it is now, not into `current`: a row taken
            // off with "not interested" during the wait would otherwise be written
            // back. A page that failed to arrive leaves what is on screen alone.
            _state.update { now ->
                if (now !is HomeState.Ready) return@update now
                if (page == null) {
                    now.copy(loadingMore = false)
                } else {
                    now.copy(
                        videos = now.videos.appendNew(page.videos),
                        nextPageToken = page.nextPageToken,
                        loadingMore = false,
                    )
                }
            }
        }
    }

    private fun load(showLoading: Boolean, refreshing: Boolean = false) {
        val previous = _state.value as? HomeState.Ready
        if (showLoading) _state.value = HomeState.Loading
        else if (refreshing && previous != null) _state.update { previous.copy(refreshing = true) }

        loading?.cancel()
        loadingMore?.cancel()
        loading = viewModelScope.launch {
            _state.value = runCatching {
                val page = fetch(pageToken = "")
                HomeState.Ready(videos = page.videos, nextPageToken = page.nextPageToken)
            }
                .onFailure { if (it is CancellationException) throw it }
                .fold(
                    onSuccess = { it },
                    onFailure = ::asState,
                )
        }
    }

    /** Where a page comes from, decided once by the kind of chip. */
    private suspend fun fetch(pageToken: String): FeedPage = when (chip) {
        Chip.Live -> FeedPage(videos = videos.live(), nextPageToken = "")
        Chip.Missed -> videos.missed(pageToken = pageToken)
        Chip.All -> videos.feed(topic = "", pageToken = pageToken)
        is Chip.Category -> videos.feed(topic = chip.query, pageToken = pageToken)
        // Never reached: More is a navigation button, not a feed, and is not one
        // of the pager's pages.
        Chip.More -> FeedPage(emptyList(), "")
    }

    private fun asState(error: Throwable): HomeState =
        error.toScreenState(HomeState.NeedsServer, HomeState::Failed)
}
