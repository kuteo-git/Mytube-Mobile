package com.mytube.app.ui

import com.mytube.app.domain.model.Video

/**
 * The next page, minus anything already on the page.
 *
 * **A duplicate row is a crash, not a repetition.** Every feed in this app is
 * a `LazyColumn` keyed on the video id, and Compose throws out of `subcompose`
 * during measure when one key arrives twice — which reaches the phone as an
 * unhandled Kotlin exception and `SIGABRT`. Measured on 2026-09-06, scrolling
 * the Missed chip: `LazyListMeasuredItemProvider.getAndMeasure` → `subcompose`
 * in `lastExceptionBacktrace`.
 *
 * And a duplicate is not a fault to be fixed at the other end: these endpoints
 * page by **offset**, so a video ingested between two requests shifts the
 * window and hands back a row that is already on screen. That is ordinary and
 * the server is not wrong to do it. What the client cannot do is promise
 * unique keys it has not checked.
 *
 * The row already shown is the one kept — it is what somebody may be looking
 * at, and replacing it would move the list under them for no gain.
 */
fun List<Video>.appendNew(page: List<Video>): List<Video> {
    val seen = mapTo(mutableSetOf()) { it.id }
    return this + page.filter { seen.add(it.id) }
}
