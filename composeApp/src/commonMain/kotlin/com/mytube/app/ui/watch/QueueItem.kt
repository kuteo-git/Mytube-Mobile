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
 */
data class QueueItem(
    val id: String,
    /** @see Video.sourceUrl */
    val sourceUrl: String = "",
    /** @see Video.inLibrary */
    val inLibrary: Boolean = true,
)

fun Video.asQueueItem() = QueueItem(id = id, sourceUrl = sourceUrl, inLibrary = inLibrary)
