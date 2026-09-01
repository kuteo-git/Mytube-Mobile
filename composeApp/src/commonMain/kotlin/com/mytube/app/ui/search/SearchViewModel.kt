package com.mytube.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytube.app.data.repository.ServerNotConfigured
import com.mytube.app.domain.model.ExternalVideo
import com.mytube.app.domain.model.Video
import com.mytube.app.domain.repository.VideoRepository
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
 * A search is a full-text query plus a recorded behaviour signal — the gateway
 * records one per request — so typing "nothing phone" without this is thirteen
 * queries and thirteen signals for one intent, and recsys would be learning
 * every prefix of what somebody typed.
 */
private const val DEBOUNCE_MILLIS = 300L

/**
 * How many upstream results to ask for, and how many more each time.
 *
 * The web app's number. Upstream has no cursor — `ytsearchN:` takes a count — so
 * "more" is the same search asked for at a larger size, and the answer is
 * complete when it comes back shorter than what was asked for.
 */
internal const val UPSTREAM_PAGE = 20

sealed interface SearchState {
    /** Nothing typed yet. Not an empty result: nobody has asked anything. */
    data object Idle : SearchState
    data object Searching : SearchState
    data object NeedsServer : SearchState
    data class Failed(val message: String) : SearchState
    data class Ready(val query: String, val videos: List<Video>) : SearchState
}

/**
 * The YouTube half, which fails on its own.
 *
 * A second state rather than a field on [SearchState], because the two halves
 * are two round trips: the library answers off a local index, and this one
 * drives yt-dlp against the internet. One state for both would mean a screen
 * that cannot show the half that worked — and the half that works is usually
 * the local one, which is the half somebody is most likely to want.
 */
sealed interface UpstreamState {
    data object Idle : UpstreamState
    data object Searching : UpstreamState
    /** Asked and refused. The next keystroke is the retry; there is no button. */
    data object Failed : UpstreamState
    data class Ready(
        val videos: List<ExternalVideo>,
        /** True while a *larger* page is on its way, with these still on screen. */
        val loadingMore: Boolean = false,
        /** False once an answer comes back shorter than what was asked for. */
        val hasMore: Boolean = true,
    ) : UpstreamState
}

class SearchViewModel(private val videos: VideoRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _upstream = MutableStateFlow<UpstreamState>(UpstreamState.Idle)
    val upstream: StateFlow<UpstreamState> = _upstream.asStateFlow()

    /**
     * The `sourceUrl` of the card being opened, or empty.
     *
     * Opening an upstream result is a real round trip — the gateway asks YouTube
     * for the metadata before there is anything to play — so the card says so
     * and refuses a second tap. Navigating first and letting the watch screen
     * find nothing would put "YouTube will not hand this video over" on screen
     * for a video that is on its way, which is the lie this app has now told
     * twice for other reasons.
     */
    private val _opening = MutableStateFlow("")
    val opening: StateFlow<String> = _opening.asStateFlow()

    /** True when the last attempt to open an upstream video failed. */
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
    private var upstreamLimit = UPSTREAM_PAGE

    fun type(text: String) {
        _query.value = text
        // Cancelled on every keystroke, so only the last one survives the wait.
        // Without this the debounce delays each request without reducing how
        // many are eventually made.
        pending?.cancel()

        _channelPasted.value = ""

        if (text.isBlank()) {
            _state.value = SearchState.Idle
            _upstream.value = UpstreamState.Idle
            return
        }

        // A new question, so the page size goes back to its first size. Without
        // this, refining a word inherits however large the previous search had
        // grown — a hundred upstream results for a word just typed.
        upstreamLimit = UPSTREAM_PAGE

        pending = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            // Both halves, side by side and after the same wait.
            //
            // Not a fallback for an empty library: the gateway's own note says
            // upstream search "runs on every search, not only when the library
            // comes up empty — topics decide what the feed offers, and searching
            // is how someone deliberately looks past that."
            launch { searchLibrary(text) }
            launch { searchUpstream() }
            launch { resolveChannel(text) }
        }
    }

    private suspend fun searchLibrary(text: String) {
        // Only blank the screen on the *first* search. Replacing results
        // with a spinner on every keystroke makes a list that is mostly
        // still correct flicker away while somebody refines a word.
        if (_state.value !is SearchState.Ready) _state.value = SearchState.Searching
        _state.value = runCatching { videos.search(text) }.fold(
            onSuccess = { SearchState.Ready(text, it) },
            onFailure = ::asState,
        )
    }

    private suspend fun searchUpstream() {
        val text = _query.value
        val asked = upstreamLimit
        if (_upstream.value !is UpstreamState.Ready) _upstream.value = UpstreamState.Searching
        _upstream.value = runCatching { videos.discover(text, asked) }.fold(
            onSuccess = {
                UpstreamState.Ready(videos = it, hasMore = it.size >= asked)
            },
            // No message drawn. The library half already says what went wrong
            // when *it* fails, and there is nothing a reader can do about
            // yt-dlp's — the next keystroke is the retry.
            onFailure = { UpstreamState.Failed },
        )
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
     * checked for being an address; the two halves already running will answer.
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
     * Ask for a larger upstream page.
     *
     * Refused while one is already in flight and once the answers have run out,
     * so reaching the bottom of a finished list costs nothing.
     */
    fun loadMoreUpstream() {
        val ready = _upstream.value as? UpstreamState.Ready ?: return
        if (ready.loadingMore || !ready.hasMore || _query.value.isBlank()) return

        _upstream.value = ready.copy(loadingMore = true)
        upstreamLimit += UPSTREAM_PAGE
        viewModelScope.launch { searchUpstream() }
    }

    /**
     * Open an upstream result, writing its catalogue row first if it has none.
     *
     * `inLibrary` skips the round trip: the row is already there, and the id the
     * gateway would answer with is the id already in hand.
     */
    fun openExternal(video: ExternalVideo, onOpened: (String) -> Unit) {
        if (_opening.value.isNotEmpty()) return
        _openFailed.value = false

        if (video.inLibrary) {
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
        _query.value = ""
        _state.value = SearchState.Idle
        _upstream.value = UpstreamState.Idle
        _channelPasted.value = ""
        upstreamLimit = UPSTREAM_PAGE
    }

    fun retry() {
        val text = _query.value
        if (text.isNotBlank()) type(text)
    }

    private fun asState(error: Throwable): SearchState = when (error) {
        is ServerNotConfigured -> SearchState.NeedsServer
        else -> SearchState.Failed(error.message ?: "could not reach the library")
    }
}
