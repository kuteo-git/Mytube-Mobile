package com.mytube.app.ui.tv

import com.mytube.app.ui.toScreenState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.domain.model.Topic
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.FeedPage
import com.mytube.app.domain.repository.VideoRepository
import com.mytube.app.ui.appendNew
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What one shelf on the television's Home is a shelf *of*.
 *
 * The phone's chips, laid out the other way: where a phone shows one chip's
 * list at a time and asks somebody to pick, a television shows every chip as a
 * row and lets the remote move down through them. The order is the chip row's —
 * Missed and Live straight after the ranked feed's place, then the topics — with
 * Continue watching first, where the phone draws its rail.
 */
sealed interface ShelfKind {
    val key: String

    data object ContinueWatching : ShelfKind {
        override val key = "__continue"
    }

    /** The phone's Missed chip: absent when nothing was missed. */
    data object Missed : ShelfKind {
        override val key = "__missed"
    }

    /** The phone's Live chip: absent when nothing is on air. */
    data object Live : ShelfKind {
        override val key = "__live"
    }

    /** The ranked feed, which is the phone's All chip. */
    data object Recommended : ShelfKind {
        override val key = "__all"
    }

    data class OfTopic(val topic: Topic) : ShelfKind {
        // Namespaced, because a topic is the library's own label and could be
        // called `__all`: a bare name collided with the fixed shelves' keys,
        // and one lazy key twice in a list is a crash.
        override val key: String = "$TOPIC_KEY_PREFIX${topic.name}"
    }
}

/** What every topic shelf's key starts with, and no fixed shelf's does. */
internal const val TOPIC_KEY_PREFIX = "topic:"

/**
 * Where a shelf's first page is.
 *
 * `Waiting` and `Loading` are different answers, which is the charter's "none
 * yet and still asking": a topic shelf is not asked for until the remote is
 * near it, so a dozen topics cost a dozen requests only for somebody who scrolls
 * through all of them.
 */
enum class ShelfLoad { Waiting, Loading, Ready, Failed }

data class Shelf(
    val kind: ShelfKind,
    val videos: List<Video>,
    /** Empty when there is no next page — Live and Continue watching never have one. */
    val nextPageToken: String,
    val load: ShelfLoad,
    val loadingMore: Boolean,
) {
    /**
     * Whether the row is drawn at all.
     *
     * Not once it has answered with nothing: a titled row with no cards under
     * it is the empty grid a chip with no videos would have led to, which the
     * phone refuses for the same reason. Still asking, or failed, it is drawn —
     * the first says something is coming and the second has a retry.
     */
    val shows: Boolean get() = load != ShelfLoad.Ready || videos.isNotEmpty()

    val canLoadMore: Boolean
        get() = load == ShelfLoad.Ready && !loadingMore && nextPageToken.isNotEmpty()
}

sealed interface TvHomeState {
    data object Loading : TvHomeState
    data object NeedsServer : TvHomeState
    data class Failed(val message: String) : TvHomeState
    data class Ready(val shelves: List<Shelf>) : TvHomeState
}

/**
 * The shelves, in order, from what the first load brought back.
 *
 * A named function with a test, because which rows exist is a rule two sources
 * decide — what the server sent and what the phone's chip row would have shown
 * — and getting it wrong draws a row titled "Live" over nothing.
 *
 * Topic shelves arrive `Waiting`: their videos are asked for when the remote
 * reaches them. A topic the server counts no videos for is not a shelf, the
 * same as it is not a chip.
 */
internal fun shelvesFrom(
    continueWatching: List<Video>,
    missed: FeedPage?,
    live: List<Video>,
    recommended: FeedPage,
    topics: List<Topic>,
): List<Shelf> {
    fun ready(kind: ShelfKind, videos: List<Video>, token: String = "") =
        Shelf(kind, videos, token, ShelfLoad.Ready, loadingMore = false)

    return buildList {
        add(ready(ShelfKind.ContinueWatching, continueWatching))
        add(ready(ShelfKind.Missed, missed?.videos.orEmpty(), missed?.nextPageToken.orEmpty()))
        add(ready(ShelfKind.Live, live))
        add(ready(ShelfKind.Recommended, recommended.videos, recommended.nextPageToken))
        topics.filter { it.videoCount > 0 }.forEach { topic ->
            add(Shelf(ShelfKind.OfTopic(topic), emptyList(), "", ShelfLoad.Waiting, loadingMore = false))
        }
    }
}

/**
 * The shelves after [TvHomeViewModel.catchUp]: [fresh]'s rows in [fresh]'s
 * order, except that a topic shelf already on screen keeps itself.
 *
 * The fixed shelves are what watching something changes — Continue watching,
 * Missed — and they are a single answer each, so they are taken whole. A topic
 * shelf is its own request, made when the remote reached it and maybe paged
 * since: arriving `Waiting` again would draw its skeleton under the remote. A
 * topic the server no longer has goes; a new one arrives `Waiting`.
 */
internal fun caughtUp(now: List<Shelf>, fresh: List<Shelf>): List<Shelf> {
    val kept = now.filter { it.kind is ShelfKind.OfTopic }.associateBy { it.kind.key }
    return fresh.map { kept[it.kind.key] ?: it }
}

/**
 * The television's Home: every chip as a shelf.
 *
 * Its own ViewModel rather than [com.mytube.app.ui.home.HomeViewModel] with a
 * flag, because the two answer different questions. That one holds *one* list
 * and a chip saying which; this holds many lists, each with its own page token
 * and its own request in flight. The phone's would have to grow a map of
 * everything it keeps singly, and every one of its tests would be about a
 * screen that is not the one it draws.
 */
class TvHomeViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _state = MutableStateFlow<TvHomeState>(TvHomeState.Loading)
    val state: StateFlow<TvHomeState> = _state.asStateFlow()

    /**
     * The first load, and each shelf's own request.
     *
     * Kept so a refresh can cancel all of them. A shelf's page that lands after
     * the refresh would otherwise be merged into the new rows — the last
     * question wins, not the last answer.
     */
    private var loading: Job? = null
    private val shelfJobs = mutableMapOf<String, Job>()

    init {
        load()
    }

    fun refresh() = load()

    /**
     * Ask again with the shelves left on screen: what coming back to Home
     * does.
     *
     * Home stays composed under the player, so a video watched from it was
     * missing from Continue watching until the app was restarted — reported
     * that way. [refresh] would put the skeleton up and take away the card
     * focus is handed back to; this swaps the answers in under the remote, and
     * an answer that fails leaves what was there. Topic shelves already asked
     * for keep what they hold — see [caughtUp].
     */
    fun catchUp() {
        if (_state.value !is TvHomeState.Ready) return load()
        loading?.cancel()
        loading = viewModelScope.launch {
            val fresh = runCatching { fetch() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull() ?: return@launch
            // The fixed shelves' next pages belong to the answers being
            // replaced — cancelled now, as the new ones land, and not before:
            // the shelves stayed usable while this was asked, so a next page
            // may have been asked for since; and an answer that failed must
            // leave the one in flight to land, or its row would wait on it
            // for ever (`loadingMore` with nothing coming).
            shelfJobs.keys.filterNot { it.startsWith(TOPIC_KEY_PREFIX) }.forEach { shelfJobs.remove(it)?.cancel() }
            _state.update { latest ->
                if (latest is TvHomeState.Ready) TvHomeState.Ready(caughtUp(latest.shelves, fresh)) else latest
            }
        }
    }

    private fun load() {
        loading?.cancel()
        shelfJobs.values.forEach { it.cancel() }
        shelfJobs.clear()
        _state.value = TvHomeState.Loading
        loading = viewModelScope.launch {
            _state.value = runCatching { fetch() }
                .onFailure { if (it is CancellationException) throw it }
                .fold(onSuccess = { TvHomeState.Ready(it) }, onFailure = ::asState)
        }
    }

    private suspend fun fetch(): List<Shelf> = coroutineScope {
        // In parallel: five independent questions to one server, which is the
        // phone's Home's arrangement and for its reason.
        val history = async { videos.history().filter { it.isInProgress }.take(12) }
        // Guarded like the phone's: Missed is a row that may simply not exist,
        // and a server that cannot answer it has not failed to answer Home.
        val missed = async { runCatching { videos.missed() }.getOrNull() }
        val live = async { videos.live() }
        val recommended = async { videos.feed() }
        val topics = async { videos.topics() }
        shelvesFrom(
            continueWatching = history.await(),
            missed = missed.await(),
            live = live.await(),
            recommended = recommended.await(),
            topics = topics.await(),
        )
    }

    /**
     * Ask for a shelf's first page, if nobody has yet.
     *
     * Called by the row as it is composed, which is "the remote is near it" —
     * a lazy column composes a little past what is on screen, so the cards are
     * usually there by the time focus arrives.
     */
    fun ensureLoaded(kind: ShelfKind) {
        val shelf = shelf(kind) ?: return
        if (shelf.load != ShelfLoad.Waiting) return
        fetchFirstPage(kind)
    }

    /** A shelf that failed, asked again. */
    fun retry(kind: ShelfKind) {
        val shelf = shelf(kind) ?: return
        if (shelf.load != ShelfLoad.Failed) return
        fetchFirstPage(kind)
    }

    private fun fetchFirstPage(kind: ShelfKind) {
        val topic = (kind as? ShelfKind.OfTopic)?.topic?.name ?: return
        updateShelf(kind) { it.copy(load = ShelfLoad.Loading) }
        shelfJobs[kind.key]?.cancel()
        shelfJobs[kind.key] = viewModelScope.launch {
            val page = runCatching { videos.feed(topic = topic) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            updateShelf(kind) {
                if (page == null) {
                    it.copy(load = ShelfLoad.Failed)
                } else {
                    it.copy(
                        videos = page.videos,
                        nextPageToken = page.nextPageToken,
                        load = ShelfLoad.Ready,
                    )
                }
            }
        }
    }

    /**
     * The next page of one shelf, appended to its end.
     *
     * Asked when focus nears the end of the row. A page that fails leaves the
     * row as it was: taking away what is on screen because the *next* page did
     * not arrive is the phone feed's rule too.
     */
    fun loadMore(kind: ShelfKind) {
        val shelf = shelf(kind) ?: return
        if (!shelf.canLoadMore) return
        val token = shelf.nextPageToken
        updateShelf(kind) { it.copy(loadingMore = true) }
        shelfJobs[kind.key] = viewModelScope.launch {
            val page = runCatching {
                when (kind) {
                    ShelfKind.Missed -> videos.missed(pageToken = token)
                    ShelfKind.Recommended -> videos.feed(pageToken = token)
                    is ShelfKind.OfTopic -> videos.feed(topic = kind.topic.name, pageToken = token)
                    // Lists, not feeds: everything arrives in the first answer.
                    ShelfKind.ContinueWatching, ShelfKind.Live -> null
                }
            }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            updateShelf(kind) {
                if (page == null) {
                    it.copy(loadingMore = false)
                } else {
                    it.copy(
                        // Merged, never concatenated: a video ranked onto two
                        // pages is one repeated lazy key, and a repeated key is
                        // a crash rather than a repetition.
                        videos = it.videos.appendNew(page.videos),
                        nextPageToken = page.nextPageToken,
                        loadingMore = false,
                    )
                }
            }
        }
    }

    private fun shelf(kind: ShelfKind): Shelf? =
        (_state.value as? TvHomeState.Ready)?.shelves?.firstOrNull { it.kind.key == kind.key }

    /** Into the state as it is *now*, so two shelves answering at once both land. */
    private fun updateShelf(kind: ShelfKind, change: (Shelf) -> Shelf) = _state.update { now ->
        if (now !is TvHomeState.Ready) return@update now
        now.copy(shelves = now.shelves.map { if (it.kind.key == kind.key) change(it) else it })
    }

    private fun asState(error: Throwable): TvHomeState =
        error.toScreenState(TvHomeState.NeedsServer, TvHomeState::Failed)
}
