package com.mytube.app.ui.watch

import com.mytube.app.domain.model.Video

/**
 * One row of the list a video was opened from.
 *
 * It used to be the id alone, and that was enough for as long as every queue
 * came from the catalogue. A **channel page's** does not: its uploads come from
 * YouTube, so most rows have never been near this disk, and pressing next asked
 * `/api/videos/{id}` for one of them — which the gateway answers 404 for, and
 * the watch screen turned into *"YouTube will not hand this video over"* for a
 * video YouTube was serving perfectly well.
 *
 * `ChannelViewModel.openVideo` had always known this and wrote the row before
 * navigating; its own comment claimed the rest of the page *"takes this same
 * path when it is reached"*, and nothing did — next never went through it. So
 * the queue carries what that decision needs rather than an id somebody has to
 * look the answer up for: the address to write, and whether it has to be
 * written at all.
 *
 * **Neither field has a default, and `inLibrary` is why.** A default of `true`
 * reads as "do not write this", so a caller that forgot the flag would get back
 * exactly the 404 this type was added to stop, with nothing reporting it. That
 * is the trap `startAtBeginning` was already caught by — *"it bought one short
 * call site and paid for it with a fault nothing can catch"* — and here the
 * convenient default is the bug itself.
 */
data class QueueItem(
    /**
     * The row as the page drew it — up next draws it again — and as it is now:
     * once the sitting writes it into the catalogue, under the id it was
     * written as and marked as in the library ([written]).
     */
    val video: Video,
) {
    val id: String get() = video.id
    /** @see Video.sourceUrl */
    val sourceUrl: String get() = video.sourceUrl
    /** @see Video.inLibrary */
    val inLibrary: Boolean get() = video.inLibrary

    /**
     * This row, now in the catalogue under [id].
     *
     * The address is carried rather than dropped: it is the only thing a row
     * can be re-resolved from, and nothing is gained by forgetting it.
     */
    fun written(id: String) = QueueItem(video.copy(id = id, inLibrary = true))
}

fun Video.asQueueItem() = QueueItem(this)

/**
 * What a speaker should play for [videoId] opened from [queue]: the video, then
 * the rest of the list after it. Rows the library does not hold yet are left
 * out — the cast service plays what the catalogue has. A video not in the list
 * goes alone, and whoever sends it decides what follows.
 */
fun castQueueOf(videoId: String, queue: List<QueueItem>): List<String> {
    val here = queue.indexOfFirst { it.id == videoId }
    val rest = if (here >= 0) queue.drop(here + 1).filter { it.inLibrary }.map { it.id } else emptyList()
    return listOf(videoId) + rest
}
