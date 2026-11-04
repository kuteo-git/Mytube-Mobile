package com.mytube.app.ui.search

import com.mytube.app.ui.toScreenState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How long to wait after the last keystroke before asking.
 *
 * Every search is a yt-dlp run against the internet, so typing "nothing phone"
 * without this is thirteen upstream requests for one intent.
 */
private const val DEBOUNCE_MILLIS = 300L

/**
 * How many results to ask for, and how many more each time.
 *
 * The web app's number. Upstream has no cursor — `ytsearchN:` takes a count — so
 * "more" is the same search asked for at a larger size, and the answer is
 * complete when it comes back shorter than what was asked for.
 */
internal const val UPSTREAM_PAGE = 20

/**
 * A search, which asks YouTube and nothing else.
 *
 * It used to ask the library as well and draw the two answers as two halves.
 * Asked to stop: what somebody types into search is what they would type into
 * YouTube, and a result already on the disk is still in YouTube's answer,
 * marked by [Video.inLibrary] and opened without a round trip.
 */
sealed interface SearchState {
    /**
     * No answer: nothing typed, or the first question still out (see
     * [SearchViewModel.asking]). Not an empty result. The suggestions stay up.
     */
    data object Idle : SearchState
    data object NeedsServer : SearchState
    data class Failed(val message: String) : SearchState
    data class Ready(
        /** What these videos answer, which the list's scroll position belongs to. */
        val query: String,
        val videos: List<Video>,
        /** True while a *larger* page is on its way, with these still on screen. */
        val loadingMore: Boolean = false,
        /** False once an answer comes back shorter than what was asked for. */
        val hasMore: Boolean = true,
    ) : SearchState
}

class SearchViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /**
     * True from a keystroke until its answer, and while a larger page is on its
     * way — what the field's spinner shows.
     *
     * Not derivable from [state]: refining a word keeps the last answer on
     * screen as `Ready` while the next is asked for, so nothing in it says a
     * question is out.
     */
    private val _asking = MutableStateFlow(false)
    val asking: StateFlow<Boolean> = _asking.asStateFlow()

    /**
     * Home's first page, shown under "Suggested videos" while nothing is typed
     * and under an answer that found nothing.
     *
     * Asked once, as the screen opens, and kept for the sitting: clearing the
     * field is not a reason to ask again. A failure is an empty list and the
     * section is not drawn — the search still works without it.
     */
    private val _suggestions = MutableStateFlow<List<Video>>(emptyList())
    val suggestions: StateFlow<List<Video>> = _suggestions.asStateFlow()

    /**
     * The `sourceUrl` of the card being opened, or empty.
     *
     * Opening a result is a real round trip — the gateway asks YouTube for the
     * metadata before there is anything to play — so the card says so and
     * refuses a second tap. Navigating first and letting the watch screen find
     * nothing would put "YouTube will not hand this video over" on screen for a
     * video that is on its way, which is the lie this app has now told twice for
     * other reasons.
     */
    private val _opening = MutableStateFlow("")
    val opening: StateFlow<String> = _opening.asStateFlow()

    /** True when the last attempt to open a result failed. */
    private val _openFailed = MutableStateFlow(false)
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    /**
     * The channel a pasted address named, or empty.
     *
     * A value the screen acts on and then clears, rather than a callback fired
     * from here: this is set inside a coroutine that outlives a frame, and a
     * navigation performed from there would happen whether or not the screen was
     * still on top of the app.
     */
    private val _channelPasted = MutableStateFlow("")
    val channelPasted: StateFlow<String> = _channelPasted.asStateFlow()

    private var pending: Job? = null
    /** A larger page on its way. A new question cancels it: the last question wins. */
    private var more: Job? = null
    private var limit = UPSTREAM_PAGE

    init {
        viewModelScope.launch {
            runCatching { videos.feed() }
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { page -> _suggestions.value = page.videos.distinctBy { it.id } }
        }
    }

    fun type(text: String) {
        _query.value = text
        // Cancelled on every keystroke, so only the last one survives the wait.
        // Without this the debounce delays each request without reducing how
        // many are eventually made.
        pending?.cancel()
        more?.cancel()

        _channelPasted.value = ""
        // A failed open belongs to the last answer; it is not news on the next.
        _openFailed.value = false

        if (text.isBlank()) {
            _state.value = SearchState.Idle
            _asking.value = false
            return
        }
        _asking.value = true

        // A new question, so the page size goes back to its first size. Without
        // this, refining a word inherits however large the previous search had
        // grown — a hundred results for a word just typed.
        limit = UPSTREAM_PAGE

        pending = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            launch { search(text) }
            launch { resolveChannel(text) }
        }
    }

    private suspend fun search(text: String) {
        val asked = limit
        // The last answer stays on screen while the next is asked for: a list
        // replaced by a spinner on every keystroke flickers away while somebody
        // refines a word. The field's spinner says a question is out.
        _state.value = runCatching { videos.discover(text, asked) }
            // A search replaced by the next keystroke is cancelled, and
            // `runCatching` catches that like any error — which drew a failure
            // over a query nobody was waiting on any more.
            .onFailure { if (it is CancellationException) throw it }
            .fold(
                // `distinctBy` for the reason `appendNew` exists: rows are keyed,
                // and a key arriving twice throws out of `subcompose` during
                // measure. This is yt-dlp's answer, which promises nothing.
                onSuccess = { SearchState.Ready(text, it.distinctBy { v -> v.id }, hasMore = it.size >= asked) },
                onFailure = { error -> keptAfterAFailedPage(text) ?: asState(error) },
            )
        _asking.value = false
    }

    /**
     * The page already read, when what failed was a larger one of the same
     * question: the reader keeps the list, and the next reach for the bottom
     * asks for that size again.
     */
    private fun keptAfterAFailedPage(text: String): SearchState? {
        val ready = _state.value as? SearchState.Ready ?: return null
        if (!ready.loadingMore || ready.query != text) return null
        limit -= UPSTREAM_PAGE
        return ready.copy(loadingMore = false)
    }

    /**
     * Is this a channel's address rather than a question?
     *
     * Asked of every query, because only the gateway can tell — it reads the
     * address *and* checks whether the library already knows the handle, which
     * it does for 1,626 of its 1,690 channels, so the usual case costs no
     * upstream request at all.
     *
     * A failure is silence. Nothing is wrong with a search that could not be
     * checked for being an address; the search already running will answer.
     */
    private suspend fun resolveChannel(text: String) {
        val id = runCatching { videos.resolveChannel(text) }.getOrDefault("")
        // Guarded against arriving late for a query nobody is looking at any
        // more: the job is cancelled on the next keystroke, but this check costs
        // nothing and says what the guarantee is.
        if (id.isNotEmpty() && _query.value == text) _channelPasted.value = id
    }

    /** Said by the screen once it has gone there, so it does not go twice. */
    fun channelOpened() {
        _channelPasted.value = ""
    }

    /**
     * Ask for a larger page.
     *
     * Refused while one is already in flight and once the answers have run out,
     * so reaching the bottom of a finished list costs nothing.
     */
    fun loadMore() {
        val ready = _state.value as? SearchState.Ready ?: return
        if (ready.loadingMore || !ready.hasMore || ready.query != _query.value) return

        _state.value = ready.copy(loadingMore = true)
        _asking.value = true
        limit += UPSTREAM_PAGE
        more = viewModelScope.launch { search(ready.query) }
    }

    /**
     * Open a result or a suggestion, writing its catalogue row first if it has
     * none.
     *
     * `inLibrary` skips the round trip: the row is already there, and the id the
     * gateway would answer with is the id already in hand.
     */
    fun open(video: Video, onOpened: (String) -> Unit) {
        if (_opening.value.isNotEmpty()) return
        _openFailed.value = false

        if (video.inLibrary || video.sourceUrl.isEmpty()) {
            onOpened(video.id)
            return
        }

        _opening.value = video.sourceUrl
        viewModelScope.launch {
            runCatching { videos.ensureExternal(video.sourceUrl) }
                .onSuccess { id -> if (id.isNotEmpty()) onOpened(id) else _openFailed.value = true }
                .onFailure { _openFailed.value = true }
            _opening.value = ""
        }
    }

    fun clear() {
        pending?.cancel()
        more?.cancel()
        _query.value = ""
        _state.value = SearchState.Idle
        _asking.value = false
        _channelPasted.value = ""
        _openFailed.value = false
        limit = UPSTREAM_PAGE
    }

    /** A suggestion taken off the list and out of the feed, as on Home. */
    fun notInterested(video: Video) {
        _suggestions.value = _suggestions.value.filterNot { it.id == video.id }
        viewModelScope.launch { runCatching { videos.setNotInterested(video.id) } }
    }

    fun retry() {
        val text = _query.value
        if (text.isNotBlank()) type(text)
    }

    private fun asState(error: Throwable): SearchState =
        error.toScreenState(SearchState.NeedsServer, SearchState::Failed)
}
