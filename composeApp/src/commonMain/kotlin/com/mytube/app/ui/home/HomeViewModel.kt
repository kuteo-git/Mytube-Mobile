package com.mytube.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The chip row above the feed.
 *
 * `All` and `Live` are not topics and are modelled as such. Live especially:
 * the server answers it from a different endpoint, filtered to the viewer's own
 * subscriptions, and — per the charter — *"the chip is absent when nothing is on
 * air. A red dot is a claim that something is happening; one lit over an empty
 * grid teaches people to stop believing it."*
 */
sealed interface Chip {
    data object All : Chip
    data object Live : Chip
    data class Category(val topic: Topic) : Chip

    val key: String
        get() = when (this) {
            All -> "__all"
            Live -> "__live"
            is Category -> topic.name
        }
}

sealed interface HomeState {
    data object Loading : HomeState
    data object NeedsServer : HomeState
    data class Failed(val message: String) : HomeState

    data class Ready(
        val videos: List<Video>,
        val nextPageToken: String,
        val chips: List<Chip> = emptyList(),
        val selected: Chip = Chip.All,
        /**
         * Partly watched, for the rail above the feed.
         *
         * Empty under a topic chip, deliberately: the web app's reasoning is
         * that *"under a topic the page is answering a narrower question, and a
         * global mix would be beside the point."*
         */
        val continueWatching: List<Video> = emptyList(),
        val loadingMore: Boolean = false,
        /** A pull-to-refresh is in flight; the list below stays on screen. */
        val refreshing: Boolean = false,
        /**
         * A different topic is being fetched.
         *
         * Distinct from `Loading`, and the distinction is the whole point: going
         * to `Loading` destroys the composable that holds the chip row, so the
         * row was rebuilt with its scroll at zero — press a chip off to the
         * right and it jumped back to the left, taking the chip just pressed out
         * of view. Staying in `Ready` keeps the chips exactly where they are and
         * shows the skeleton only where the videos will go.
         */
        val switching: Boolean = false,
    ) : HomeState
}

/**
 * The home screen's state holder.
 *
 * Four sources feed it — the ranked feed, the topic list, what is on air, and
 * recent history — and they are fetched together rather than in sequence: they
 * are independent questions to one server on the same wifi, and asking them one
 * after another would make the screen four round trips slow for no reason.
 */
class HomeViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        load(Chip.All, showLoading = true)
    }

    fun refresh() {
        val chip = (_state.value as? HomeState.Ready)?.selected ?: Chip.All
        // A pull-to-refresh keeps what is on screen and marks itself busy; only
        // a first load or a retry after failure blanks the page. Replacing a
        // working feed with a spinner because somebody tugged it is taking away
        // what they were reading.
        val keepingContent = _state.value is HomeState.Ready
        load(chip, showLoading = !keepingContent, refreshing = keepingContent)
    }

    fun select(chip: Chip) {
        val current = _state.value
        if (current is HomeState.Ready && current.selected == chip) return

        if (current is HomeState.Ready) {
            // The chips and their scroll position stay; only the videos go.
            _state.value = current.copy(
                selected = chip,
                videos = emptyList(),
                continueWatching = emptyList(),
                nextPageToken = "",
                switching = true,
            )
            load(chip, showLoading = false)
            return
        }
        load(chip, showLoading = true)
    }

    /**
     * Keep this video's file against the eviction sweep, or stop keeping it.
     *
     * Drawn first and put back if refused, the same rule the watch screen's
     * buttons follow — this is a statement about the viewer's own shelf, and a
     * control that waits for a round trip feels broken.
     */
    fun toggleSaved(video: Video) {
        val current = _state.value as? HomeState.Ready ?: return
        val next = !video.saved
        _state.value = current.copy(
            videos = current.videos.map { if (it.id == video.id) it.copy(saved = next) else it },
        )
        viewModelScope.launch {
            runCatching { videos.setSaved(video.id, next) }.onFailure {
                val now = _state.value
                if (now is HomeState.Ready) _state.value = now.copy(videos = current.videos)
            }
        }
    }

    /**
     * Take it off the page, and tell the ranker.
     *
     * Removed from the list rather than greyed out. The action means "not this
     * one", and leaving it on screen makes somebody press it twice to check it
     * worked. It is not put back if the request fails: the row is gone from the
     * viewer's page either way, and returning it a second later is the more
     * confusing outcome — the next feed load is where the truth shows.
     */
    fun notInterested(video: Video) {
        val current = _state.value as? HomeState.Ready ?: return
        _state.value = current.copy(videos = current.videos.filterNot { it.id == video.id })
        viewModelScope.launch { runCatching { videos.setNotInterested(video.id) } }
    }

    /**
     * "I have already seen this."
     *
     * Taken off the rail at once and recorded as fully watched, so the server
     * agrees — the ranker already drops anything past 95% from Home, so telling
     * it the truth is what makes this outlive the app it was pressed in. The
     * local removal is what covers the seconds until the feed is next fetched.
     */
    fun markWatched(video: Video) {
        val current = _state.value as? HomeState.Ready ?: return
        _state.value = current.copy(
            continueWatching = current.continueWatching.filterNot { it.id == video.id },
        )
        viewModelScope.launch {
            runCatching {
                videos.recordProgress(
                    videoId = video.id,
                    positionSeconds = video.durationSeconds.toDouble(),
                    watchedFraction = 1.0,
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current !is HomeState.Ready || current.loadingMore || current.refreshing) return
        // A switch is already fetching this topic's first page; a second request
        // would append it to itself.
        if (current.switching) return
        if (current.nextPageToken.isEmpty()) return
        // Live is a list, not a feed: the server sends everything on air in one
        // answer with no page token, so there is nothing to ask for.
        if (current.selected == Chip.Live) return

        _state.update { current.copy(loadingMore = true) }
        viewModelScope.launch {
            _state.value = runCatching {
                videos.feed(topic = topicOf(current.selected), pageToken = current.nextPageToken)
            }.fold(
                onSuccess = {
                    current.copy(
                        videos = current.videos + it.videos,
                        nextPageToken = it.nextPageToken,
                        loadingMore = false,
                    )
                },
                // A page that failed to arrive leaves what is already on screen
                // alone. Replacing a working list with an error because the
                // *next* page failed takes away what the viewer had.
                onFailure = { current.copy(loadingMore = false) },
            )
        }
    }

    private fun load(chip: Chip, showLoading: Boolean, refreshing: Boolean = false) {
        val previous = _state.value as? HomeState.Ready
        if (showLoading) _state.value = HomeState.Loading
        else if (refreshing && previous != null) _state.update { previous.copy(refreshing = true) }

        viewModelScope.launch {
            _state.value = runCatching { fetch(chip, previous) }.fold(
                onSuccess = { it },
                onFailure = ::asState,
            )
        }
    }

    private suspend fun fetch(chip: Chip, previous: HomeState.Ready?): HomeState.Ready =
        coroutineScope {
            // In parallel: four independent questions to one server.
            val feed = async {
                if (chip == Chip.Live) null else videos.feed(topic = topicOf(chip))
            }
            val live = async { videos.live() }
            // The chip row and the rail are only rebuilt on a full load. Keeping
            // the previous ones during a topic switch is what stops the chips
            // flickering out and back as the feed under them changes.
            val topics = async { previous?.chips?.takeIf { it.isNotEmpty() } ?: buildChips() }
            val history = async {
                if (chip == Chip.All) videos.history().filter { it.isInProgress }.take(12)
                else emptyList()
            }

            val onAir = live.await()
            val page = feed.await()

            HomeState.Ready(
                videos = if (chip == Chip.Live) onAir else page?.videos.orEmpty(),
                nextPageToken = page?.nextPageToken.orEmpty(),
                chips = withLive(topics.await(), onAir.isNotEmpty()),
                selected = chip,
                continueWatching = history.await(),
            )
        }

    private suspend fun buildChips(): List<Chip> =
        listOf(Chip.All) + videos.topics()
            // A chip that leads to an empty grid is a dead button wearing a
            // category name.
            .filter { it.videoCount > 0 }
            .map { Chip.Category(it) }

    /**
     * The Live chip appears only when something is on air.
     *
     * Rebuilt each load rather than stored, because "is anything broadcasting"
     * is exactly the thing that changes between one look and the next.
     */
    private fun withLive(chips: List<Chip>, anythingOnAir: Boolean): List<Chip> {
        val withoutLive = chips.filterNot { it == Chip.Live }
        if (!anythingOnAir) return withoutLive
        return listOf(withoutLive.first(), Chip.Live) + withoutLive.drop(1)
    }

    private fun topicOf(chip: Chip): String = when (chip) {
        is Chip.Category -> chip.topic.name
        else -> ""
    }

    private fun asState(error: Throwable): HomeState = when (error) {
        is ServerNotConfigured -> HomeState.NeedsServer
        else -> HomeState.Failed(error.message ?: "could not reach the library")
    }
}
