package com.mytube.app.domain.model

/**
 * Playing on the household's speakers — the R1 and the Google Nest speakers.
 *
 * The session is the server's (the cast service, through the gateway): every
 * phone sees the same one, and closing this app changes nothing on the speaker.
 * This app asks and shows; it never talks to a speaker itself.
 */
enum class CastDeviceKind { R1, Google }

data class CastDevice(
    val id: String,
    val name: String,
    val kind: CastDeviceKind,
)

enum class CastState { Loading, Playing, Paused, Ended }

/**
 * Why a session ended. [Running] while it has not: absence named rather than a
 * null, the charter's rule for the domain.
 */
enum class CastEnd {
    Running,
    QueueFinished,
    Stopped,
    Replaced,
    TakenOver,
    StoppedOnDevice,
    DeviceOffline,
    Handoff,
    Failed;

    /**
     * Whether the end needs saying. Pressing stop, the queue running out and
     * handing back here are the viewer's own acts; the speaker being taken,
     * stopped on the speaker, lost or failing are not, and a remote that just
     * closed on them would look broken.
     */
    val needsSaying: Boolean
        get() = this == TakenOver || this == StoppedOnDevice || this == DeviceOffline ||
            this == Failed || this == Replaced
}

data class CastItem(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
)

data class CastSession(
    val id: String,
    val device: CastDevice,
    val state: CastState,
    val end: CastEnd,
    val queue: List<CastItem>,
    val currentIndex: Int,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val volume: Int,
    /** Changes with the queue, never with the position. A queue edit names it. */
    val version: Int,
) {
    val current: CastItem? get() = queue.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex < queue.size - 1
}

/** Why the gateway refused a cast request, as something a screen can word. */
enum class CastRefusal { NotFound, StaleQueue, Ended, Live, Offline, Unreachable }

/** What a cast request came back with. */
sealed interface CastAnswer<out T> {
    data class Done<T>(val value: T) : CastAnswer<T>
    data class Refused(val why: CastRefusal) : CastAnswer<Nothing>
}

/** Where the speaker was when it handed back, for this phone to carry on. */
data class CastHandback(val videoId: String, val positionSeconds: Int)

/** The queue with one item moved, for a move-up or move-down press. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}

/**
 * Where the current song lands after an edit, so it keeps playing. The server
 * checks this too; sending the right index spares it a reload.
 */
fun CastSession.indexAfterEdit(edited: List<CastItem>): Int {
    val playing = current ?: return 0
    val found = edited.indexOfFirst { it.videoId == playing.videoId }
    if (found >= 0) return found
    return minOf(currentIndex, (edited.size - 1).coerceAtLeast(0))
}
