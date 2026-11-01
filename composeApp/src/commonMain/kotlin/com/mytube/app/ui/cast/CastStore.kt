package com.mytube.app.ui.cast

import com.mytube.app.domain.model.CastAnswer
import com.mytube.app.domain.model.CastDevice
import com.mytube.app.domain.model.CastEnd
import com.mytube.app.domain.model.CastHandback
import com.mytube.app.domain.model.CastItem
import com.mytube.app.domain.model.CastRefusal
import com.mytube.app.domain.model.CastSession
import com.mytube.app.domain.model.CastState
import com.mytube.app.domain.model.indexAfterEdit
import com.mytube.app.domain.model.moved
import com.mytube.app.domain.repository.CastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the remote is showing. */
sealed interface CastRemote {
    data object Closed : CastRemote
    /** Asked for, not yet answered. */
    data class Opening(val sessionId: String) : CastRemote
    data class Showing(val session: CastSession) : CastRemote
}

/** Something to tell the viewer once. */
sealed interface CastNotice {
    data object None : CastNotice
    data class Ended(val end: CastEnd) : CastNotice
    data class Refused(val why: CastRefusal) : CastNotice
    data object QueueReloaded : CastNotice
}

data class CastUiState(
    val devices: List<CastDevice> = emptyList(),
    /** Asking for the speakers, as against having asked and found none. */
    val devicesLoading: Boolean = false,
    val pickerOpen: Boolean = false,
    /** Sessions running anywhere in the house, for the bar on every page. */
    val running: List<CastSession> = emptyList(),
    val remote: CastRemote = CastRemote.Closed,
    val notice: CastNotice = CastNotice.None,
    /** Asking whether to stop casting or carry on, after the lit cast button was pressed again. */
    val asking: Boolean = false,
) {
    /** Something is playing on a speaker: the cast button is drawn lit. */
    val casting: Boolean get() = running.isNotEmpty() || remote !is CastRemote.Closed
}

/**
 * How often to ask about sessions, or null for not at all.
 *
 * Every second while the remote is open, so a pause pressed on another phone
 * shows here within two; every five otherwise, which is only for the bar; never
 * in the background, where nobody is looking and the OS would rather we slept.
 */
fun castPollMillis(remoteOpen: Boolean, foreground: Boolean): Long? = when {
    !foreground -> null
    remoteOpen -> 1_000L
    else -> 5_000L
}

/**
 * The household's speakers, held for the life of the app.
 *
 * Not a screen's ViewModel: the bar is on every page and the remote outlives the
 * page it was opened from, so this lives beside the container and is told when
 * the app goes to the background.
 */
class CastStore(
    private val repository: CastRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state.asStateFlow()

    private var foreground = true
    private var poller: Job? = null
    // Whether the first answer since the app started has been read. A session
    // already playing then is the app being reopened mid-cast, and it comes
    // back as it was left — on the remote. Later ones are another phone's
    // doing and only light the button.
    private var startChecked = false

    init {
        repoll()
    }

    private val remoteId: String
        get() = when (val r = _state.value.remote) {
            CastRemote.Closed -> ""
            is CastRemote.Opening -> r.sessionId
            is CastRemote.Showing -> r.session.id
        }

    private val showing: CastSession?
        get() = (_state.value.remote as? CastRemote.Showing)?.session

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        repoll()
    }

    private fun repoll() {
        poller?.cancel()
        val every = castPollMillis(remoteId.isNotEmpty(), foreground) ?: return
        poller = scope.launch {
            while (isActive) {
                pollOnce()
                delay(every)
            }
        }
    }

    private suspend fun pollOnce() {
        val id = remoteId
        if (id.isNotEmpty()) {
            refresh(id)
            return
        }
        when (val answer = repository.sessions()) {
            is CastAnswer.Done -> {
                _state.update { it.copy(running = answer.value) }
                if (!startChecked) {
                    startChecked = true
                    val playing = answer.value.firstOrNull()
                    if (playing != null && _state.value.remote == CastRemote.Closed) {
                        _state.update { it.copy(remote = CastRemote.Showing(playing)) }
                        repoll()
                    }
                }
            }
            is CastAnswer.Refused -> Unit
        }
    }

    private suspend fun refresh(id: String) {
        when (val answer = repository.session(id)) {
            is CastAnswer.Done -> show(answer.value)
            is CastAnswer.Refused -> if (answer.why == CastRefusal.NotFound) close(CastNotice.None)
        }
    }

    private fun show(session: CastSession) {
        if (session.state == CastState.Ended) {
            close(if (session.end.needsSaying) CastNotice.Ended(session.end) else CastNotice.None)
            return
        }
        if (remoteId == session.id) _state.update { it.copy(remote = CastRemote.Showing(session)) }
    }

    private fun close(notice: CastNotice) {
        _state.update { it.copy(remote = CastRemote.Closed, notice = notice, running = emptyList()) }
        repoll()
    }

    /** Asked once when a player appears, and again whenever the picker opens. */
    fun loadDevices() {
        _state.update { it.copy(devicesLoading = true) }
        scope.launch {
            val devices = repository.devices()
            _state.update { it.copy(devices = devices, devicesLoading = false) }
        }
    }

    fun openPicker() {
        _state.update { it.copy(pickerOpen = true) }
        loadDevices()
    }

    fun closePicker() = _state.update { it.copy(pickerOpen = false) }

    fun start(device: CastDevice, videoIds: List<String>, positionSeconds: Double) {
        _state.update { it.copy(pickerOpen = false) }
        scope.launch {
            when (val answer = repository.start(device.id, videoIds, positionSeconds.roundToInt().coerceAtLeast(0))) {
                is CastAnswer.Done -> {
                    _state.update { it.copy(remote = CastRemote.Showing(answer.value)) }
                    repoll()
                }
                is CastAnswer.Refused -> _state.update { it.copy(notice = CastNotice.Refused(answer.why)) }
            }
        }
    }

    /**
     * Another song picked in the app while a speaker plays: it goes to the same
     * speaker, from the start, and the remote stays as it was.
     */
    fun recast(videoIds: List<String>) {
        val playing = showing ?: _state.value.running.firstOrNull() ?: return
        if (videoIds.isEmpty()) return
        scope.launch {
            when (val answer = repository.start(playing.device.id, videoIds, 0)) {
                is CastAnswer.Done -> {
                    _state.update {
                        if (it.remote == CastRemote.Closed) it.copy(running = listOf(answer.value))
                        else it.copy(remote = CastRemote.Showing(answer.value))
                    }
                    repoll()
                }
                is CastAnswer.Refused -> _state.update { it.copy(notice = CastNotice.Refused(answer.why)) }
            }
        }
    }

    fun openRemote(sessionId: String) {
        _state.update { it.copy(remote = CastRemote.Opening(sessionId)) }
        repoll()
    }

    fun closeRemote() {
        _state.update { it.copy(remote = CastRemote.Closed) }
        repoll()
    }

    fun noticeShown() = _state.update { it.copy(notice = CastNotice.None) }

    private fun command(call: suspend (String) -> CastAnswer<CastSession>) {
        val id = remoteId.ifEmpty { return }
        scope.launch {
            when (val answer = call(id)) {
                is CastAnswer.Done -> show(answer.value)
                is CastAnswer.Refused -> when (answer.why) {
                    CastRefusal.StaleQueue -> {
                        _state.update { it.copy(notice = CastNotice.QueueReloaded) }
                        refresh(id)
                    }
                    CastRefusal.Ended, CastRefusal.NotFound -> refresh(id)
                    else -> _state.update { it.copy(notice = CastNotice.Refused(answer.why)) }
                }
            }
        }
    }

    fun pause() = command(repository::pause)
    fun resume() = command(repository::resume)
    fun next() = command(repository::next)
    fun stop() = command(repository::stop)
    fun seek(seconds: Int) = command { repository.seek(it, seconds) }
    fun volume(percent: Int) = command { repository.volume(it, percent) }
    fun jump(index: Int) = command { repository.jump(it, index) }

    private fun edit(change: (List<CastItem>) -> List<CastItem>) {
        val session = showing ?: return
        val edited = change(session.queue)
        if (edited.isEmpty() || edited == session.queue) return
        command { repository.replaceQueue(it, session.version, edited.map(CastItem::videoId), session.indexAfterEdit(edited)) }
    }

    fun moveUp(index: Int) = edit { it.moved(index, index - 1) }
    fun moveDown(index: Int) = edit { it.moved(index, index + 1) }
    fun remove(index: Int) = edit { q -> q.filterIndexed { i, _ -> i != index } }

    /** The lit cast button, pressed: ask whether to stop or carry on. */
    fun askAboutCasting() = _state.update { it.copy(asking = true) }

    /** Carry on casting, on the remote. */
    fun keepCasting() {
        val session = _state.value.running.firstOrNull()
        _state.update { it.copy(asking = false) }
        if (session != null && remoteId.isEmpty()) {
            _state.update { it.copy(remote = CastRemote.Showing(session)) }
            repoll()
        }
    }

    /** Stop casting and carry on here. */
    fun stopCasting(onHandback: (CastHandback) -> Unit) {
        val id = remoteId.ifEmpty { _state.value.running.firstOrNull()?.id.orEmpty() }
        _state.update { it.copy(asking = false) }
        if (id.isNotEmpty()) handoff(id, onHandback)
    }

    fun dismissAsking() = _state.update { it.copy(asking = false) }

    /** Stops the speaker and hands its position to [onHandback], which carries on here. */
    fun handoff(onHandback: (CastHandback) -> Unit) = handoff(remoteId, onHandback)

    private fun handoff(id: String, onHandback: (CastHandback) -> Unit) {
        if (id.isEmpty()) return
        scope.launch {
            when (val answer = repository.handoff(id)) {
                is CastAnswer.Done -> {
                    close(CastNotice.None)
                    onHandback(answer.value)
                }
                is CastAnswer.Refused -> _state.update { it.copy(notice = CastNotice.Refused(answer.why)) }
            }
        }
    }
}
