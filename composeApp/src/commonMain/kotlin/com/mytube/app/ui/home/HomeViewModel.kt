package com.mytube.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.appendNew
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
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

    /**
     * New uploads from followed channels, not yet watched.
     *
     * Not a topic, like [Live] and for the same two reasons: the server answers
     * it from a different endpoint, and it is **absent when the answer is
     * empty** — a chip leading to a blank grid is a dead button wearing a name.
     * "Nothing was missed" is a true and useful answer, and it is said by the
     * chip not being there.
     */
    data object Missed : Chip
    data object Live : Chip
    data class Category(val topic: Topic) : Chip

    val key: String
        get() = when (this) {
            All -> "__all"
            Missed -> "__missed"
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
            // What is on screen now goes back in the drawer before anything is
            // taken off it.
            remember(current)

            val seen = cache[chip.key]
            if (seen != null) {
                // Straight back to what was there, then refreshed underneath.
                //
                // A chip already visited used to clear its videos and show the
                // skeleton again, so stepping to Live and back to All cost a
                // round trip and threw away the reading position. The list is
                // still good; it is only possibly stale, and stale is a reason
                // to reload quietly, not a reason to show nothing.
                _state.value = current.copy(
                    selected = chip,
                    videos = seen.videos,
                    continueWatching = seen.continueWatching,
                    nextPageToken = seen.nextPageToken,
                    switching = false,
                )
                // **And then, usually, nothing.**
                //
                // It used to reload every time, quietly: the cached list
                // appeared and a moment later a different one replaced it under
                // whoever was already reading it. Invisible was the intent and
                // it is not what a list changing by itself looks like — reported
                // as exactly that, stepping between two chips.
                //
                // So the reload is a question about *age*. Inside the window the
                // list on screen is the answer, and refreshing is a gesture
                // somebody makes rather than a thing that happens to them; past
                // it the page is old enough that showing yesterday's answer
                // would be the worse fault. Pull-to-refresh is unconditional and
                // always was.
                if (seen.at.elapsedNow() >= STALE_AFTER) load(chip, showLoading = false)
                return
            }

            // Never seen: the skeleton is honest, there is nothing to show.
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
     * What each chip was showing, so going back to one is instant.
     *
     * In the ViewModel rather than the screen because it is fetched data, and it
     * dies with the ViewModel — which is keyed on the server address, so
     * pointing the app at another machine cannot show that machine's feed under
     * this one's chips.
     */
    private data class CachedChip(
        val videos: List<Video>,
        val continueWatching: List<Video>,
        val nextPageToken: String,
        /** When this was fetched, for [STALE_AFTER]. */
        val at: TimeMark,
    )

    private val cache = mutableMapOf<String, CachedChip>()

    private fun remember(ready: HomeState.Ready) {
        if (ready.videos.isEmpty()) return
        cache[ready.selected.key] = CachedChip(
            videos = ready.videos,
            continueWatching = ready.continueWatching,
            nextPageToken = ready.nextPageToken,
            at = TimeSource.Monotonic.markNow(),
        )
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
                // The one place the kind of chip decides where a page comes
                // from. Everything else reads a token it was handed.
                if (current.selected == Chip.Missed) {
                    videos.missed(pageToken = current.nextPageToken)
                } else {
                    videos.feed(topic = topicOf(current.selected), pageToken = current.nextPageToken)
                }
            }.fold(
                onSuccess = {
                    current.copy(
                        videos = current.videos.appendNew(it.videos),
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
                when (chip) {
                    Chip.Live, Chip.Missed -> null
                    else -> videos.feed(topic = topicOf(chip))
                }
            }
            val live = async { videos.live() }
            // Asked on every load, not only when this chip is selected — the
            // same arrangement Live has, and for its reason: whether the chip
            // exists at all is decided by whether the answer is empty, so the
            // question has to be asked before anybody can press it.
            //
            // One request, two jobs. It draws the chip, and when the chip is the
            // one selected it *is* the page — so nothing is fetched twice.
            val missed = async { runCatching { videos.missed() }.getOrNull() }
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
            val missedPage = missed.await()

            HomeState.Ready(
                videos = when (chip) {
                    Chip.Live -> onAir
                    Chip.Missed -> missedPage?.videos.orEmpty()
                    else -> page?.videos.orEmpty()
                },
                nextPageToken = when (chip) {
                    Chip.Live -> ""
                    Chip.Missed -> missedPage?.nextPageToken.orEmpty()
                    else -> page?.nextPageToken.orEmpty()
                },
                chips = withMissed(
                    withLive(topics.await(), onAir.isNotEmpty()),
                    missedPage?.videos?.isNotEmpty() == true,
                ),
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

    /**
     * The Missed chip appears only when something was missed.
     *
     * Rebuilt each load like [withLive] rather than remembered, because "is
     * there anything new I have not seen" is exactly the thing that changes
     * between one look and the next — and it changes by *watching*, which is
     * what the viewer does between two looks at this screen.
     *
     * Placed straight after All and before Live: it is a way of reading the
     * whole library rather than a subject, and it answers the question people
     * open the app to ask.
     */
    private fun withMissed(chips: List<Chip>, anythingMissed: Boolean): List<Chip> {
        val without = chips.filterNot { it == Chip.Missed }
        if (!anythingMissed || without.isEmpty()) return without
        return listOf(without.first(), Chip.Missed) + without.drop(1)
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

/**
 * How long a chip's page is treated as still true.
 *
 * Five minutes, and the number is a judgement about *this* feed: the library
 * gains videos in batches when a scan runs, not continuously, so a page is
 * rarely wrong within a few minutes — and stepping between two chips to compare
 * them takes seconds, which is the case the reload was ruining.
 *
 * It bounds the staleness rather than removing it: past this a chip that has
 * been sitting in the cache since the app opened this morning is reloaded, which
 * is the fault the unconditional reload was written to prevent.
 */
private val STALE_AFTER = 5.minutes
